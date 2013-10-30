package fly.play.s3

import scala.concurrent.Future

trait BucketLike {
  def url(itemName: String, expires: Long): String
  def get(itemName: String): Future[BucketFile]
  def list: Future[Iterable[BucketItem]]
  def list(prefix: String): Future[Iterable[BucketItem]]
  def + = add _
  def add(bucketFile: BucketFile): Future[Unit]
  def - = remove _
  def remove(itemName: String): Future[Unit]
  def withDelimiter(delimiter: String): BucketLike
  def withDelimiter(delimiter: Option[String]): BucketLike
  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ): Future[Unit]

  def delimiter: Option[String]
}
