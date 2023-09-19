package besom.examples.lambda

import java.io.*
import java.nio.charset.StandardCharsets
import org.apache.commons.fileupload.*
import scala.jdk.CollectionConverters.*

object MultipartParser:

  private class SimpleContext(requestBody: Array[Byte], contentTypeHeader: String) extends UploadContext:
    def contentLength: Long = requestBody.length
    def getCharacterEncoding: String =
      val parser = ParameterParser()
      parser.setLowerCaseNames(true)
      val charset = parser.parse(contentTypeHeader, ';').get("charset")
      if charset != null then charset else "UTF-8"

    def getContentLength: Int       = requestBody.length
    def getContentType: String      = contentTypeHeader
    def getInputStream: InputStream = ByteArrayInputStream(requestBody)

  private class MemoryFileItem(
    var fieldName: String,
    var contentType: String,
    var isFormField: Boolean,
    var fileName: String
  ) extends FileItem:
    private val os                       = ByteArrayOutputStream()
    private var headers: FileItemHeaders = _

    def delete(): Unit = ()

    def get: Array[Byte] = os.toByteArray

    def getContentType: String = contentType

    def getFieldName: String = fieldName

    def getInputStream: InputStream = ByteArrayInputStream(get)

    def getName: String = fileName

    def getOutputStream: OutputStream = os

    def getSize: Long = os.size

    def getString: String = String(get, StandardCharsets.UTF_8)

    def getString(encoding: String): String = String(get, encoding)

    def isInMemory: Boolean = true

    def setFieldName(name: String): Unit = fieldName = name

    def setFormField(state: Boolean): Unit = isFormField = state

    def write(file: File): Unit = ()

    def getHeaders: FileItemHeaders = headers

    def setHeaders(headers: FileItemHeaders): Unit = this.headers = headers

  private class MemoryFileItemFactory extends FileItemFactory:
    def createItem(fieldName: String, contentType: String, isFormField: Boolean, fileName: String): FileItem =
      MemoryFileItem(fieldName, contentType, isFormField, fileName)

  def parseRequest(requestBody: Array[Byte], contentTypeHeader: String): Map[String, List[FileItem]] =
    val fileUpload = FileUpload(MemoryFileItemFactory())
    fileUpload.parseParameterMap(SimpleContext(requestBody, contentTypeHeader)).asScala.toMap.map { case (key, items) =>
      key -> items.asScala.toList
    }
