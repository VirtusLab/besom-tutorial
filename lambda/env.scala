package besom.examples.lambda

import software.amazon.awssdk.regions.Region

object Env:
  lazy val region: Region     = Region.of(sys.env.getOrElse("AWS_REGION", sys.env("AWS_DEFAULT_REGION")))
  lazy val bucketName: String = sys.env("BUCKET_NAME")
  lazy val stage: String      = sys.env("STAGE")
