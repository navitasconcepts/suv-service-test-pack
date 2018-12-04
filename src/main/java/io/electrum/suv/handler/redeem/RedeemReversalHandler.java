package io.electrum.suv.handler.redeem;

import io.electrum.suv.api.models.ErrorDetail;
import io.electrum.suv.api.models.RedemptionRequest;
import io.electrum.suv.handler.BaseHandler;
import io.electrum.suv.resource.impl.SUVTestServer;
import io.electrum.suv.server.SUVTestServerRunner;
import io.electrum.suv.server.util.RequestKey;
import io.electrum.suv.server.util.VoucherModelUtils;
import io.electrum.vas.model.BasicReversal;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.concurrent.ConcurrentHashMap;

public class RedeemReversalHandler extends BaseHandler {
   public RedeemReversalHandler(HttpHeaders httpHeaders) {
      super(httpHeaders);
   }

   public Response handle(BasicReversal reversal, UriInfo uriInfo) {
      try {
         Response rsp;

         // The UUID of this request
         String reversalUuid = reversal.getId();
         // The UUID identifying the request that this reversal relates to
         String voucherId = reversal.getRequestId();

         if (!VoucherModelUtils.isValidUuid(reversalUuid)) {
            return VoucherModelUtils.buildInvalidUuidErrorResponse(
                  reversalUuid,
                  null, // TODO Could overload method
                  username,
                  ErrorDetail.ErrorType.FORMAT_ERROR);
         } else if (!VoucherModelUtils.isValidUuid(voucherId)) {
            return VoucherModelUtils
                  .buildInvalidUuidErrorResponse(voucherId, null, username, ErrorDetail.ErrorType.FORMAT_ERROR);
         }

         // TODO check this in airtime
         rsp = VoucherModelUtils.canReverseRedemption(voucherId, reversalUuid, username, password);
         if (rsp != null) {
            if (rsp.getStatus() == 404) {
               // make sure to record the reversal in case we get the request late.
               addRedemptionReversalToCache(reversal);
            }
            return rsp;
         }

         addRedemptionReversalToCache(reversal);

         rsp = Response.accepted((reversal)).build(); // TODO Ask Casey if this is ok

         return rsp;

      } catch (Exception e) {
         return logAndBuildException(e);
      }
   }

   /**
    * Must check for a corresponding redemption request to get voucher from so that vouchers state may be updated. If no
    * corresponding redmption exists, that voucher must still exist in provision_confirmed state (this is fine) //TODO Docs
    */
   private void addRedemptionReversalToCache(BasicReversal basicReversal) {
      ConcurrentHashMap<RequestKey, BasicReversal> reversalRecords =
            SUVTestServerRunner.getTestServer().getRedemptionReversalRecords();
      RequestKey reversalKey =
            new RequestKey(username, password, RequestKey.REVERSALS_RESOURCE, basicReversal.getRequestId());

      ConcurrentHashMap<String, SUVTestServer.VoucherState> confirmedExistingVouchers =
            SUVTestServerRunner.getTestServer().getConfirmedExistingVouchers();
      ConcurrentHashMap<RequestKey, RedemptionRequest> redemptionRequestRecords =
            SUVTestServerRunner.getTestServer().getRedemptionRequestRecords();

      RedemptionRequest redemptionRequest = redemptionRequestRecords.get(reversalKey);
      reversalRecords.put(reversalKey, basicReversal);
      if (redemptionRequest != null) {
         confirmedExistingVouchers.put(redemptionRequest.getVoucher().getCode(), SUVTestServer.VoucherState.REDEEMED);
      }
   }

   @Override
   protected String getRequestName() {
      return "Redeem Reversal";
   }
}
