/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.saga;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cucumber.api.DataTable;
import cucumber.api.java.After;
import cucumber.api.java8.En;

public class PackStepdefs implements En {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ALPHA_REST_ADDRESS = "alpha.rest.address";
  private static final String CAR_SERVICE_ADDRESS = "car.service.address";
  private static final String HOTEL_SERVICE_ADDRESS = "hotel.service.address";
  private static final String BOOKING_SERVICE_ADDRESS = "booking.service.address";
  private static final String[] addresses = {CAR_SERVICE_ADDRESS, HOTEL_SERVICE_ADDRESS};

  private static final Consumer<Map<String, String>[]> NO_OP_CONSUMER = (dataMap) -> {
  };

  public PackStepdefs() {
    Given("^Car Service is up and running$", () -> {
      probe(System.getProperty(CAR_SERVICE_ADDRESS));
    });

    And("^Hotel Service is up and running$", () -> {
      probe(System.getProperty(HOTEL_SERVICE_ADDRESS));
    });

    And("^Booking Service is up and running$", () -> {
      probe(System.getProperty(BOOKING_SERVICE_ADDRESS));
    });

    And("^Alpha is up and running$", () -> {
      probe(System.getProperty(ALPHA_REST_ADDRESS));
    });

    When("^User ([A-Za-z]+) requests to book ([0-9]+) cars and ([0-9]+) rooms$", (username, cars, rooms) -> {
      log.info("Received request from user {} to book {} cars and {} rooms", username, cars, rooms);

      given()
          .pathParam("name", username)
          .pathParam("rooms", rooms)
          .pathParam("cars", cars)
          .when()
          .post(System.getProperty("booking.service.address") + "/booking/{name}/{rooms}/{cars}")
          .then()
          .statusCode(is(200));
    });

    Then("^Alpha records the following events$", (DataTable dataTable) -> {
      Consumer<Map<String, String>[]> columnStrippingConsumer = dataMap -> {
        for (Map<String, String> map : dataMap)
          map.keySet().retainAll(dataTable.topCells());
      };

      dataMatches(System.getProperty(ALPHA_REST_ADDRESS) + "/events", dataTable, columnStrippingConsumer);
    });

    And("^Car Service contains the following booking orders$", (DataTable dataTable) -> {
      dataMatches(System.getProperty(CAR_SERVICE_ADDRESS) + "/bookings", dataTable, NO_OP_CONSUMER);
    });

    And("^Hotel Service contains the following booking orders$", (DataTable dataTable) -> {
      dataMatches(System.getProperty(HOTEL_SERVICE_ADDRESS) + "/bookings", dataTable, NO_OP_CONSUMER);
    });
  }

  @After
  public void cleanUp() {
    log.info("Cleaning up services");
    for (String address : addresses) {
      given()
          .when()
          .delete(System.getProperty(address) + "/bookings")
          .then()
          .statusCode(is(200));
    }

    given()
        .when()
        .delete(System.getProperty(ALPHA_REST_ADDRESS) + "/events")
        .then()
        .statusCode(is(200));
  }

  @SuppressWarnings("unchecked")
  private void dataMatches(String address, DataTable dataTable, Consumer<Map<String, String>[]> dataProcessor) {
    Map<String, String>[] dataMap = given()
        .when()
        .get(address)
        .then()
        .statusCode(is(200))
        .extract()
        .body()
        .as(Map[].class);

    dataProcessor.accept(dataMap);

    log.info("Retrieved data {} from service", dataMap);
    dataTable.diff(DataTable.create(asList(dataMap)));
  }

  private void probe(String address) {
    log.info("Connecting to service address {}", address);
    given()
        .when()
        .get(address + "/info")
        .then()
        .statusCode(is(200));
  }
}
