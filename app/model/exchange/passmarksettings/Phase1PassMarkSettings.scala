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

package model.exchange.passmarksettings

import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.Macros

// format: OFF
case class Phase1PassMarkSettings(
                                   schemes: List[Phase1PassMark],
                                   version: String,
                                   createDate: DateTime,
                                   createdBy: String
)

// format: ON
object Phase1PassMarkSettings {
  import repositories.BSONDateTimeHandler
  implicit val phase1PassMarkSettings = Json.format[Phase1PassMarkSettings]
  implicit val phase1PassMarkSettingsHandler = Macros.handler[Phase1PassMarkSettings]
}