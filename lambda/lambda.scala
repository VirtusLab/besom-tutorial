package besom.examples.lambda

import sttp.client4.quick.*
import sttp.client4.Response
import sttp.client4.SttpClientException
import scala.util.*
import scala.util.control.NonFatal

import upickle.*

object OptionPickler extends upickle.AttributeTagged:
  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    summon[Writer[T]].comap[Option[T]] {
      case None    => null.asInstanceOf[T]
      case Some(x) => x
    }
  override implicit def OptionReader[T: Reader]: Reader[Option[T]] =
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))):
      override def visitNull(index: Int) = None

import OptionPickler.*

case class APIGatewayProxyEvent(
  resource: String,
  path: String,
  httpMethod: String,
  headers: Map[String, String],
  multiValueHeaders: Map[String, List[String]],
  queryStringParameters: Option[Map[String, String]],
  multiValueQueryStringParameters: Option[Map[String, List[String]]],
  pathParameters: Option[Map[String, String]],
  stageVariables: Option[Map[String, String]],
  requestContext: RequestContext,
  body: Option[String],
  isBase64Encoded: Boolean
) derives ReadWriter

case class RequestContext(
  resourceId: String,
  apiId: String,
  resourcePath: String,
  httpMethod: String,
  requestId: String,
  accountId: String,
  stage: String,
  identity: RequestIdentity,
  path: String
) derives ReadWriter

case class RequestIdentity(
  sourceIp: String,
  userAgent: String
) derives ReadWriter

case class APIGatewayProxyResponse(
  statusCode: Int,
  headers: Map[String, String] = Map.empty,
  body: String,
  isBase64Encoded: Boolean = false
) derives ReadWriter

def lambdaRuntime(handler: APIGatewayProxyEvent => APIGatewayProxyResponse): Unit =
  val lambdaRuntimeApi = sys.env("AWS_LAMBDA_RUNTIME_API")
  val nextInvocationUri =
    uri"http://$lambdaRuntimeApi/2018-06-01/runtime/invocation/next"
  def responseUri(requestId: String) =
    uri"http://$lambdaRuntimeApi/2018-06-01/runtime/invocation/$requestId/response"

  while true do
    Try { quickRequest.get(nextInvocationUri).send() } match
      case Failure(timeout: SttpClientException.TimeoutException) => // do nothing, try again
      case Failure(exception)                                     => throw exception

      case Success(response) =>
        val requestId = response.headers
          .find(_.name.toLowerCase == "lambda-runtime-aws-request-id")
          .getOrElse {
            throw Exception("Lambda-Runtime-Aws-Request-Id header not found")
          }
          .value
          .trim

        val event =
          try read[APIGatewayProxyEvent](response.body)
          catch
            case NonFatal(e) =>
              response.headers.foreach(h => println(s"${h.name}: ${h.value}"))
              println(response.body)
              throw e

        val lambdaResponse =
          try handler(event)
          catch
            case NonFatal(e) =>
              e.printStackTrace()
              APIGatewayProxyResponse(
                statusCode = 500,
                body = e.getMessage
              )

        val responseAsString = write(lambdaResponse)

        println(responseAsString)

        quickRequest
          .post(responseUri(requestId))
          .body(responseAsString)
          .send()
