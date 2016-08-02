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

package controllers

import model.SchemePreference
import play.api.mvc.Action
import repositories.FrameworkSchemePreferenceRepository
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

trait FrameworkSchemePreferenceController extends BaseController {
  val frameworkPreferenceRepository2: FrameworkSchemePreferenceRepository
  val auditService: AuditService

  def submitSchemePreference(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[SchemePreference]{ schemePref =>
      frameworkPreferenceRepository2.saveSchemePreference(applicationId, schemePref).map{_ => Ok}
    }
  }
}

object FrameworkSchemePreferenceController extends FrameworkSchemePreferenceController {
  val frameworkPreferenceRepository2: FrameworkSchemePreferenceRepository = repositories.frameworkSchemePreferenceRepository
  val auditService = AuditService
}
