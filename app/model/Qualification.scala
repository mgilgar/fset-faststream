/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package model

import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}
import reactivemongo.bson.{BSON, BSONHandler, BSONString}

object Qualification  extends Enumeration {
  type Qualification = Value

  val Degree_21, Degree_22, Degree_Economics, Degree_Numerate, Degree_SocialScience, Degree_CharteredEngineer = Value

  implicit val QualificationFormat = new Format[Qualification] {
    def reads(json: JsValue) = JsSuccess(Qualification.withName(json.as[String]))

    def writes(myEnum: Qualification) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, Qualification] {
    def read(doc: BSONString) = Qualification.withName(doc.value)

    def write(stats: Qualification) = BSON.write(stats.toString)
  }

}
