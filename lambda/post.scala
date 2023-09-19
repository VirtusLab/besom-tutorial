package besom.examples.lambda

import org.apache.commons.fileupload.FileItem
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.ByteBuffer
import java.util.UUID
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import software.amazon.awssdk.core.sync.RequestBody

def postCatEntry(event: APIGatewayProxyEvent): APIGatewayProxyResponse =
  try
    val requestBody =
      if event.isBase64Encoded then java.util.Base64.getDecoder().decode(event.body.getOrElse(""))
      else event.body.getOrElse("").getBytes("UTF-8")

    val items = MultipartParser.parseRequest(
      requestBody,
      event.headers.find(_._1.toLowerCase() == "content-type").map(_._2).getOrElse("")
    )

    val name        = items.get("name").flatMap(_.headOption).map(_.getString).getOrElse("")
    val comment     = items.get("comment").flatMap(_.headOption).map(_.getString).getOrElse("")
    val pictureItem = items.get("picture").flatMap(_.headOption)

    val maybePictureUrl = pictureItem.map { item =>
      val s3Client = S3Client.builder().region(Env.region).build()

      val extension = item.getContentType() match
        case "image/jpeg" => ".jpg"
        case "image/png"  => ".png"
        case "image/gif"  => ".gif"
        case _            => throw Exception("Unsupported image type")

      val objectKey = UUID.randomUUID().toString + extension

      val putRequest = PutObjectRequest.builder().bucket(Env.bucketName).key(objectKey).build()

      val requestBody = RequestBody.fromByteBuffer(ByteBuffer.wrap(item.get()))

      s3Client.putObject(putRequest, requestBody)

      s"https://${Env.bucketName}.s3.amazonaws.com/$objectKey"
    }

    val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(ZonedDateTime.now())

    val post = CatPost(
      entryId = UUID.randomUUID().toString,
      userName = name,
      comment = comment,
      timestamp = timestamp,
      catPictureURL = maybePictureUrl.getOrElse("")
    )

    dynamodb.postEntry(post, Env.region)

    APIGatewayProxyResponse(statusCode = 302, headers = Map("Location" -> s"/${Env.stage}"), body = "")
  catch
    case e: Exception =>
      e.printStackTrace()
      APIGatewayProxyResponse(statusCode = 500, body = s"""{"message": "Error: ${e.getMessage}"}""")

@main def postCatEntryMain: Unit =
  lambdaRuntime(postCatEntry)
