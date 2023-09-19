package besom.examples.lambda

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.regions.Region
import scala.jdk.CollectionConverters.*

case class CatPost(
  entryId: String,
  userName: String,
  comment: String,
  timestamp: String,
  catPictureURL: String
)

object dynamodb:
  def getEntries(region: Region): Vector[CatPost] =
    val dynamoDb = DynamoDbClient.builder().region(region).build()
    try
      val request = QueryRequest
        .builder()
        .tableName("CatPostTable")
        .keyConditionExpression("PartitionKey = :pkey")
        .expressionAttributeValues(Map(":pkey" -> AttributeValue.builder().s("ALL_ENTRIES").build()).asJava)
        .scanIndexForward(false)
        .build()

      val response = dynamoDb.query(request)
      val items    = response.items().asScala

      items
        .map(item =>
          CatPost(
            entryId = item.get("entryId").s(),
            userName = item.get("userName").s(),
            comment = item.get("comment").s(),
            timestamp = item.get("timestamp").s(),
            catPictureURL = item.get("catPictureURL").s()
          )
        )
        .toVector
    finally dynamoDb.close()

  def postEntry(post: CatPost, region: Region): Unit =
    val dynamoDb = DynamoDbClient.builder().region(region).build()
    try
      val item = Map(
        "PartitionKey" -> AttributeValue.builder().s("ALL_ENTRIES").build(),
        "entryId" -> AttributeValue.builder().s(post.entryId).build(),
        "userName" -> AttributeValue.builder().s(post.userName).build(),
        "comment" -> AttributeValue.builder().s(post.comment).build(),
        "timestamp" -> AttributeValue.builder().s(post.timestamp).build(),
        "catPictureURL" -> AttributeValue.builder().s(post.catPictureURL).build()
      )

      val putItemRequest = PutItemRequest
        .builder()
        .tableName("CatPostTable")
        .item(item.asJava)
        .build()

      val response = dynamoDb.putItem(putItemRequest)

      if (!response.sdkHttpResponse().isSuccessful()) {
        throw Exception(
          s"Unexpected response code: ${response.sdkHttpResponse().statusCode()}\n${response.sdkHttpResponse().statusText().orElse("")}"
        )
      }
    finally dynamoDb.close()
