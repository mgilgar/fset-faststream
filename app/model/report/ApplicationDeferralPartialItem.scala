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

package model.report

import model.Address

case class CandidateDeferralReportItem(
  candidateName: String,
  preferredName: String,
  email: String,
  address: Address,
  postCode: Option[String] = None,
  telephone: Option[String] = None,
  programmes: List[String] = Nil
)

object CandidateDeferralReportItem {
  implicit val format = play.api.libs.json.Json.format[CandidateDeferralReportItem]
}

case class ApplicationDeferralPartialItem(
  userId: String,
  firstName: String,
  lastName: String,
  preferredName: String,
  partnerProgrammes: List[String] = Nil
)

object ApplicationDeferralPartialItem {
  implicit val format = play.api.libs.json.Json.format[ApplicationDeferralPartialItem]
}


