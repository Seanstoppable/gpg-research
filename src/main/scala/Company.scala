
case class Company(
  name: String,
  domain: String,
  employees: _root_.scala.Option[String] = None,
  category: _root_.scala.Option[String] = None
)

object Company {
  import play.api.libs.json.__
  import play.api.libs.json.JsString
  import play.api.libs.json.Writes
  import play.api.libs.functional.syntax._

  implicit def jsonReadsCompany: play.api.libs.json.Reads[Company] = {
    (
      (__ \ "name").read[String] and
      (__ \ "domain").read[String] and
      (__ \ "employees").readNullable[String] and
      (__ \ "category").readNullable[String]
    )(Company.apply _)
  }

  def jsObjectCompany(obj: Company) = {
    play.api.libs.json.Json.obj(
      "name" -> play.api.libs.json.JsString(obj.name),
      "domain" -> play.api.libs.json.JsString(obj.domain),
      "employees" -> play.api.libs.json.Json.toJson(obj.employees),
      "category" -> play.api.libs.json.Json.toJson(obj.category)
    )
  }

  implicit def jsonWritesCompany: play.api.libs.json.Writes[Company] = {
    new play.api.libs.json.Writes[Company] {
      def writes(obj: Company) = {
        jsObjectCompany(obj)
      }
    }
  }
}
