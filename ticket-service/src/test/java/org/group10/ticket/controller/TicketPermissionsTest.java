package org.group10.ticket.controller;

import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.service.AvailabilityService;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.OrderPaymentService;
import org.group10.ticket.service.OrderQueryService;
import org.group10.ticket.service.ReservationService;
import org.group10.ticket.service.ReturnRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quién puede hacer qué en el ticket-service (US-TKT-26). El usuario sale del token: lo que pone el gateway.
 */
@WebMvcTest({OrderController.class, ReturnRequestController.class, AvailabilityController.class})
class TicketPermissionsTest {

    private static final String RESERVATION = """
            {"eventId": 1, "idempotencyKey": "k", "seatIds": [1]}""";
    private static final String PAYMENT = """
            {"paymentMethod": "CREDIT_CARD", "idempotencyKey": "k"}""";
    private static final String CLIENT_PERMISSIONS = "EVENT_SEARCH,TICKET_PURCHASE,REFUND_REQUEST";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;
    @MockitoBean
    private OrderClosingService orderClosingService;
    @MockitoBean
    private OrderPaymentService orderPaymentService;
    @MockitoBean
    private OrderQueryService orderQueryService;
    @MockitoBean
    private ReturnRequestService returnRequestService;
    @MockitoBean
    private AvailabilityService availabilityService;

    static Stream<Arguments> purchases() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/orders", RESERVATION),
                Arguments.of(HttpMethod.POST, "/api/orders/5/pay", PAYMENT),
                Arguments.of(HttpMethod.POST, "/api/orders/5/cancel", null),
                Arguments.of(HttpMethod.GET, "/api/orders", null),
                Arguments.of(HttpMethod.GET, "/api/orders/5", null));
    }

    static Stream<Arguments> refundResolutions() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/return-requests", null),
                Arguments.of(HttpMethod.POST, "/api/return-requests/9/approve", null),
                Arguments.of(HttpMethod.POST, "/api/return-requests/9/reject", null));
    }

    static Stream<Arguments> protectedEndpoints() {
        return Stream.concat(Stream.concat(purchases(), refundResolutions()),
                Stream.of(Arguments.of(HttpMethod.POST, "/api/tickets/3/return-requests", null)));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_withoutIdentity_returns401(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(call(method, path, body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(reservationService, orderClosingService, orderPaymentService, orderQueryService,
                returnRequestService);
    }

    @ParameterizedTest
    @MethodSource("purchases")
    void purchase_withoutTicketPurchase_returns403(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(call(method, path, body).header("X-User-Id", 4).header("X-User-Permissions", "TICKET_VALIDATE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 4 no tiene el permiso TICKET_PURCHASE"));
        verifyNoInteractions(reservationService, orderClosingService, orderPaymentService, orderQueryService);
    }

    @ParameterizedTest
    @MethodSource("refundResolutions")
    void refundResolution_client_returns403(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(asClient(call(method, path, body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 7 no tiene el permiso REFUND_RESOLVE"));
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void requestReturn_withoutRefundRequest_returns403() throws Exception {
        mockMvc.perform(post("/api/tickets/3/return-requests").header("X-User-Id", 4)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 4 no tiene el permiso REFUND_REQUEST"));
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void reserve_client_reservesForTheUserOfTheToken() throws Exception {
        mockMvc.perform(asClient(call(HttpMethod.POST, "/api/orders", RESERVATION))).andExpect(status().isCreated());

        verify(reservationService).reserve(eq(7L), any());
    }

    @Test
    void payAndCancel_client_actOnTheUserOfTheToken() throws Exception {
        mockMvc.perform(asClient(call(HttpMethod.POST, "/api/orders/5/pay", PAYMENT))).andExpect(status().isOk());
        mockMvc.perform(asClient(post("/api/orders/5/cancel"))).andExpect(status().isOk());

        verify(orderPaymentService).pay(eq(7L), eq(5L), any());
        verify(orderClosingService).cancelReservation(7L, 5L);
    }

    @Test
    void myOrders_client_areTheOrdersOfTheUserOfTheToken() throws Exception {
        mockMvc.perform(asClient(get("/api/orders"))).andExpect(status().isOk());
        mockMvc.perform(asClient(get("/api/orders/5"))).andExpect(status().isOk());

        verify(orderQueryService).findOrdersOfCustomer(7L);
        verify(orderQueryService).findOrderDetailOfCustomer(7L, 5L);
    }

    @Test
    void requestReturn_client_requestsItForTheUserOfTheToken() throws Exception {
        mockMvc.perform(asClient(post("/api/tickets/3/return-requests"))).andExpect(status().isCreated());

        verify(returnRequestService).requestReturn(7L, 3L);
    }

    @Test
    void refundResolution_admin_resolvesAsTheAdminOfTheToken() throws Exception {
        mockMvc.perform(asAdmin(get("/api/return-requests"))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/api/return-requests/9/approve"))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/api/return-requests/9/reject"))).andExpect(status().isOk());

        verify(returnRequestService).findByStatus(ReturnRequestStatus.PENDING, 0, 20);
        verify(returnRequestService).approve(3L, 9L);
        verify(returnRequestService).reject(3L, 9L);
    }

    @Test
    void availability_isPublic() throws Exception {
        mockMvc.perform(get("/api/availability").param("eventId", "1")).andExpect(status().isOk());

        verify(availabilityService).findAvailability(1L);
    }

    private static MockHttpServletRequestBuilder call(HttpMethod method, String path, String body) {
        MockHttpServletRequestBuilder builder = request(method, path);
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 7).header("X-User-Role", "CLIENT")
                .header("X-User-Permissions", CLIENT_PERMISSIONS);
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 3).header("X-User-Role", "ADMIN")
                .header("X-User-Permissions", CLIENT_PERMISSIONS + ",EVENT_ADMIN,REFUND_RESOLVE,USER_MANAGE");
    }
}
