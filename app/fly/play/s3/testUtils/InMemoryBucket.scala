package fly.play.s3.testUtils

import scala.concurrent.Future
import scala.collection.mutable
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.duration._
import java.util.UUID
import fly.play.s3.ACL
import fly.play.s3.BucketFile
import fly.play.s3.BucketItem
import fly.play.s3.BucketLike
import fly.play.s3.PUBLIC_READ
import fly.play.s3.S3Exception

case class InMemoryBucket(
  files:mutable.Map[String, BucketFile],
  name: String,
  delimiter: Option[String] = Some("/"),
  urlBase: String = "/inMemoryS3/") extends BucketLike {

  private def filesAsBucketItems(prefix: String = ""): Iterable[BucketItem] = {
    val filteredKeys = files.keys.filter(_ startsWith prefix)
    delimiter match {
      case None => filteredKeys.map(key => BucketItem(key, false))
      case Some(delimiter) =>
        filteredKeys.map { key =>
          val delimitedKey = key.split(delimiter).head
          BucketItem(delimitedKey, delimitedKey != key)
        }
    }
  }

  private implicit def toFuture[T](t: T): Future[T] =
    Future successful t

  private def notFound(itemName: String) =
    Future failed S3Exception(404, "NoSuchKey", s"No file with key $itemName found in memory", None)

  def get(itemName: String): Future[BucketFile] =
    files
      .get(itemName)
      .map(Future.successful)
      .getOrElse(notFound(itemName))

  def list: Future[Iterable[BucketItem]] =
    filesAsBucketItems()

  def list(prefix: String): Future[Iterable[BucketItem]] =
    filesAsBucketItems(prefix)

  def add(bucketFile: BucketFile): Future[Unit] =
    (files += bucketFile.name -> bucketFile): Unit

  def remove(itemName: String): Future[Unit] =
    (files -= itemName): Unit

  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ): Future[Unit] = {
    val oldItem = get(sourceItemName)
    oldItem.flatMap { item =>
      remove(sourceItemName).flatMap { _ =>
        add(item.copy(name = destinationItemName, acl = Some(acl)))
      }
    }
  }

  val urls = mutable.Map.empty[String, Option[String]]

  def url(itemName: String, expires: Long): String = {
    val url = urlBase + UUID.randomUUID.toString
    urls += url -> Some(itemName)
    Akka.system.scheduler.scheduleOnce(expires.seconds) {
      urls.update(url, None)
    }
    url
  }

  def fromUrl(url: String): Future[BucketFile] = 
    if (urls contains url) {
      val expired = Future failed S3Exception(403, "Forbidden", "Request has expired", None)
      val itemName = urls(url)
      itemName
        .map(get)
        .getOrElse(expired)
    }
    else notFound(url)

  def withDelimiter(delimiter: String): BucketLike =
    copy(delimiter = Some(delimiter))
  def withDelimiter(delimiter: Option[String]): BucketLike =
    copy(delimiter = delimiter)
}
