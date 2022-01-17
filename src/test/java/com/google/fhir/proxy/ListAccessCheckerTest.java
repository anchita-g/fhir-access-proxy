/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.fhir.proxy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ListAccessCheckerTest {

  private static final String TEST_LIST_ID = "test-list";
  private static final String PATIENT_AUTHORIZED = "be92a43f-de46-affa-b131-bbf9eea51140";
  private static final String PATIENT_NON_AUTHORIZED = "patient-non-authorized";
  private static final IdDt PATIENT_AUTHORIZED_ID = new IdDt("Patient", PATIENT_AUTHORIZED);
  private static final IdDt PATIENT_NON_AUTHORIZED_ID = new IdDt("Patient", PATIENT_NON_AUTHORIZED);

  @Mock private RestfulServer serverMock;

  @Mock private DecodedJWT jwtMock;

  @Mock private Claim claimMock;

  @Mock private HttpFhirClient httpFhirClientMock;

  @Mock private RequestDetails requestMock;

  // Note this is an expensive class to instantiate, so we only do this once for all tests.
  private static final FhirContext fhirContext = FhirContext.forR4();

  private ListAccessChecker.Factory testFactoryInstance;

  private void setUpFhirListSearchMock(String itemParam, String resourceFileToReturn)
      throws IOException {
    URL listUrl = Resources.getResource(resourceFileToReturn);
    String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    when(httpFhirClientMock.getResource(
            String.format("/List?_id=%s&item=%s&_elements=id", TEST_LIST_ID, itemParam)))
        .thenReturn(fhirResponseMock);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
  }

  @Before
  public void setUp() throws IOException {
    when(serverMock.getFhirContext()).thenReturn(fhirContext);
    when(jwtMock.getClaim(ListAccessChecker.PATIENT_LIST_CLAIM)).thenReturn(claimMock);
    when(claimMock.asString()).thenReturn(TEST_LIST_ID);
    setUpFhirListSearchMock("Patient/" + PATIENT_AUTHORIZED, "bundle_list_patient_item.json");
    setUpFhirListSearchMock("Patient/" + PATIENT_NON_AUTHORIZED, "bundle_empty.json");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    testFactoryInstance = new ListAccessChecker.Factory(serverMock);
  }

  @Test
  public void createTest() {
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
  }

  @Test
  public void canAccessTest() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessNotAuthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getId()).thenReturn(PATIENT_NON_AUTHORIZED_ID);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessList() throws IOException {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", TEST_LIST_ID));
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessListNotAuthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("List");
    when(requestMock.getId()).thenReturn(new IdDt("List", "wrong-id"));
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessSearchQuery() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    Map<String, String[]> params = Maps.newHashMap();
    params.put("subject", new String[] {PATIENT_AUTHORIZED});
    when(requestMock.getParameters()).thenReturn(params);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessSearchQueryNotAuthorized() {
    when(requestMock.getResourceName()).thenReturn("Observation");
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPutObservation() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Observation");
    URL listUrl = Resources.getResource("test_obs.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutObservationUnauthorized() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Observation");
    URL listUrl = Resources.getResource("test_obs_unauthorized.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPostObservation() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Observation");
    URL listUrl = Resources.getResource("test_obs.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPostObservationWithPerformer() throws IOException {
    // The sample Observation resource below has a few `performers` references in it. This is to see
    // if the `Patient` performer references are properly extracted and passed to the List query.
    when(requestMock.getResourceName()).thenReturn("Observation");
    URL listUrl = Resources.getResource("test_obs_performers.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    setUpFhirListSearchMock(
        String.format(
            "Patient/test-patient-1,Patient/%s,Patient/test-patient-2", PATIENT_AUTHORIZED),
        "bundle_list_patient_item.json");
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPostObservationNoSubject() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Observation");
    URL listUrl = Resources.getResource("test_obs_no_subject.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPostWrongQueryPath() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Encounter");
    URL listUrl = Resources.getResource("test_obs.json");
    byte[] obsBytes = Resources.toByteArray(listUrl);
    when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
  }

  @Test
  public void canAccessPostPatient() {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutExistingPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("patient_id_search_single.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  @Test
  public void canAccessPutNewPatient() throws IOException {
    when(requestMock.getResourceName()).thenReturn("Patient");
    when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);
    when(requestMock.getId()).thenReturn(PATIENT_AUTHORIZED_ID);
    URL url = Resources.getResource("bundle_empty.json");
    String testJson = Resources.toString(url, StandardCharsets.UTF_8);
    HttpResponse fhirResponseMock = Mockito.mock(HttpResponse.class);
    TestUtil.setUpFhirResponseMock(fhirResponseMock, testJson);
    when(httpFhirClientMock.getResource(
            String.format("/Patient?_id=%s&_elements=id", PATIENT_AUTHORIZED)))
        .thenReturn(fhirResponseMock);
    AccessChecker testInstance = testFactoryInstance.create(jwtMock, httpFhirClientMock);
    assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
  }

  // TODO add an Appointment POST
}
