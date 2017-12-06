/* 
 * Copyright 2016 OpenMarket Ltd
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
package org.matrix.androidsdk.rest.model.pid;

import android.content.Context;
import android.text.TextUtils;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This class provides the parameter to delete a device
 */
public class DeleteDeviceParams {
    public DeleteDeviceAuth auth;

    public static class PidResponse {
        public String mxid;
    }

    /**
     * Parameters to request a validation token for an email
     */
    public static class RequestEmailValidationParams {

        // the email address
        public String email;

        // the client secret key
        public String clientSecret;

        // the attempt count
        public Integer sendAttempt;

        // the server id
        public String id_server;

        // the nextlink (given if it is a registration process)
        public String next_link;
    }

    /**
     * Response to a request an phone number validation request
     */
    public static class RequestPhoneNumberValidationResponse {

        // the client secret key
        public String clientSecret;

        // the attempt count
        public Integer sendAttempt;

        // the sid
        public String sid;

        // the msisdn
        public String msisdn;

        // phone number international format
        public String intl_fmt;
    }

    /**
     * Parameters to request a validation token for a phone number
     */
    public static class RequestPhoneNumberValidationParams {

        // the country
        public String country;

        // the phone number
        public String phone_number;

        // the client secret key
        public String clientSecret;

        // the attempt count
        public Integer sendAttempt;

        // the server id
        public String id_server;
    }

    /**
     * Response to a request an email validation post
     */
    public static class RequestEmailValidationResponse {

        // the client secret key
        public String clientSecret;

        // the email address
        public String email;

        // the attempt count
        public Integer sendAttempt;

        // the email sid
        public String sid;
    }

    public static class ThirdPartyIdentifier implements Serializable {
        /**
         * The medium of the third party identifier (ThreePid.MEDIUM_XXX)
         */
        public String medium;

        /**
         * The third party identifier address.
         */
        public String address;

        /**
         * The timestamp in milliseconds when this 3PID has been validated.
         * Define as Object because it should be Long and it is a Double.
         * So, it might change.
         */
        public Object validatedAt;

        /**
         * The timestamp in milliseconds when this 3PID has been added to the user account.
         * Define as Object because it should be Long and it is a Double.
         * So, it might change.
         */
        public Object addedAt;
    }

    /**
     * This class describes the third party server protocols.
     */
    public static class ThirdPartyProtocol {
        // the user fields (domain, nick, username...)
        public List<String> userFields;

        // the location fields (domain, channels, room...)
        public List<String> locationFields;

        // the field types
        public Map<String, Map<String, String>> fieldTypes;

        // the protocol instance
        public List<ThirdPartyProtocolInstance> instances;
    }

    /**
     * This class describes a third party protocol instance
     */
    public static class ThirdPartyProtocolInstance implements Serializable {

        // the network identifier
        public String networkId;

        // the fields (domain...)
        public Map<String, Object> fields;

        // the instance id
        public String instanceId;

        // the description
        public String desc;

        // the dedicated bot
        public String botUserId;

        // the icon URL
        public String icon;
    }

    /**
     * 3 pid
     */
    public static class ThreePid implements Serializable {
        /**
         * Types of third party media.
         * The list is not exhaustive and depends on the Identity server capabilities.
         */
        public static final String MEDIUM_EMAIL = "email";
        public static final String MEDIUM_MSISDN = "msisdn";

        // state
        public static final int AUTH_STATE_TOKEN_UNKNOWN = 0;
        public static final int AUTH_STATE_TOKEN_REQUESTED = 1;
        public static final int AUTH_STATE_TOKEN_RECEIVED = 2;
        public static final int AUTH_STATE_TOKEN_SUBMITTED = 3;
        public static final int AUTH_STATE_TOKEN_AUTHENTIFICATED = 4;

        /**
         * Types of third party media.
         */
        public String medium;

        /**
         * The email of the user
         * Used when MEDIUM_EMAIL
         */
        public String emailAddress;

        /**
         * The phone number of the user
         * Used when MEDIUM_MSISDN
         */
        public String phoneNumber;

        /**
         * The country of the user
         * Usedwhen MEDIUM_MSISDN
         */
        public String country;

        /**
         * The current client secret key used during email validation.
         */
        public String clientSecret;

        /**
         * The current session identifier during email validation.
         */
        public String sid;

        /**
         * The number of attempts
         */
        public int sendAttempt;

        /**
         * Current validation state (AUTH_STATE_XXX)
         */
        private int mValidationState;

        /**
         * Two params constructors (MEDIUM_EMAIL)
         *
         * @param emailAddress the email address.
         * @param medium       the identifier medium, MEDIUM_EMAIL in that case
         */
        public ThreePid(String emailAddress, String medium) {
            this.medium = medium;
            this.emailAddress = emailAddress;

            if (TextUtils.equals(MEDIUM_EMAIL, this.medium) && !TextUtils.isEmpty(emailAddress)) {
                this.emailAddress = this.emailAddress.toLowerCase();
            }

            this.clientSecret = UUID.randomUUID().toString();
        }

        /**
         * Build a ThreePid with the given phone number and country (MEDIUM_MSISDN)
         *
         * @param phoneNumber the phone number (national or international format)
         * @param country     country code of the phone number (can be empty if phone number has international format and starts by "+")
         * @param medium      the identifier medium, MEDIUM_MSISDN in that case
         */
        public ThreePid(String phoneNumber, String country, String medium) {
            this.medium = medium;
            this.phoneNumber = phoneNumber;
            this.country = country == null ? "" : country.toUpperCase();

            clientSecret = UUID.randomUUID().toString();
        }

        /**
         * Clear the validation parameters
         */
        private void resetValidationParameters() {
            mValidationState = AUTH_STATE_TOKEN_UNKNOWN;

            clientSecret = UUID.randomUUID().toString();
            sendAttempt = 1;
            sid = null;
        }

        /**
         * Request an email validation token.
         *
         * @param restClient           the rest client to use.
         * @param nextLink             the nextLink
         * @param isDuringRegistration true if it is added during a registration
         * @param callback             the callback when the operation is done
         */
        public void requestEmailValidationToken(final ProfileRestClient restClient, final String nextLink,
                                                final boolean isDuringRegistration, final ApiCallback<Void> callback) {
            // sanity check
            if (null != restClient && mValidationState != AUTH_STATE_TOKEN_REQUESTED) {

                if (mValidationState != AUTH_STATE_TOKEN_UNKNOWN) {
                    resetValidationParameters();
                }

                mValidationState = AUTH_STATE_TOKEN_REQUESTED;
                restClient.requestEmailValidationToken(emailAddress, clientSecret, sendAttempt, nextLink, isDuringRegistration, new ApiCallback<RequestEmailValidationResponse>() {

                    @Override
                    public void onSuccess(RequestEmailValidationResponse requestEmailValidationResponse) {

                        if (TextUtils.equals(requestEmailValidationResponse.clientSecret, clientSecret)) {
                            mValidationState = AUTH_STATE_TOKEN_RECEIVED;
                            sid = requestEmailValidationResponse.sid;
                            callback.onSuccess(null);
                        }
                    }

                    private void commonError() {
                        sendAttempt++;
                        mValidationState = AUTH_STATE_TOKEN_UNKNOWN;
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        commonError();
                        callback.onNetworkError(e);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        commonError();
                        callback.onMatrixError(e);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        commonError();
                        callback.onUnexpectedError(e);
                    }
                });

            }
        }

        /**
         * Request a phone number validation token.
         *
         * @param restClient           the rest client to use.
         * @param isDuringRegistration true if it is added during a registration
         * @param callback             the callback when the operation is done
         */
        public void requestPhoneNumberValidationToken(final ProfileRestClient restClient, final boolean isDuringRegistration,
                                                      final ApiCallback<Void> callback) {
            // sanity check
            if ((null != restClient) && (mValidationState != AUTH_STATE_TOKEN_REQUESTED)) {

                if (mValidationState != AUTH_STATE_TOKEN_UNKNOWN) {
                    resetValidationParameters();
                }

                mValidationState = AUTH_STATE_TOKEN_REQUESTED;

                restClient.requestPhoneNumberValidationToken(phoneNumber, country, clientSecret, sendAttempt, isDuringRegistration, new ApiCallback<RequestPhoneNumberValidationResponse>() {

                    @Override
                    public void onSuccess(RequestPhoneNumberValidationResponse requestPhoneNumberValidationResponse) {

                        if (TextUtils.equals(requestPhoneNumberValidationResponse.clientSecret, clientSecret)) {
                            mValidationState = AUTH_STATE_TOKEN_RECEIVED;
                            sid = requestPhoneNumberValidationResponse.sid;
                            callback.onSuccess(null);
                        }
                    }

                    private void commonError() {
                        sendAttempt++;
                        mValidationState = AUTH_STATE_TOKEN_UNKNOWN;
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        commonError();
                        callback.onNetworkError(e);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        commonError();
                        callback.onMatrixError(e);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        commonError();
                        callback.onUnexpectedError(e);
                    }
                });
            }
        }

        /**
         * Request the ownership validation of an email address or a phone number previously set
         * by {@link #requestEmailValidationToken(ProfileRestClient, String, boolean, ApiCallback)}
         *
         * @param restClient   REST client
         * @param token        the token generated by the requestEmailValidationToken or requestPhoneNumberValidationToken call
         * @param clientSecret the client secret which was supplied in the requestEmailValidationToken or requestPhoneNumberValidationToken call
         * @param sid          the sid for the session
         * @param respCallback asynchronous callback response
         */
        public void submitValidationToken(final ThirdPidRestClient restClient, final String token, final String clientSecret,
                                          final String sid, final ApiCallback<Boolean> respCallback) {
            // sanity check
            if (null != restClient) {
                restClient.submitValidationToken(medium, token, clientSecret, sid, respCallback);
            }
        }

        /**
         * Get the friendly name of the medium
         *
         * @param medium  medium of the 3pid
         * @param context the context
         * @return friendly name of the medium
         */
        public static String getMediumFriendlyName(final String medium, final Context context) {
            String mediumFriendlyName = "";
            switch (medium) {
                case MEDIUM_EMAIL:
                    mediumFriendlyName = context.getString(R.string.medium_email);
                    break;
                case MEDIUM_MSISDN:
                    mediumFriendlyName = context.getString(R.string.medium_phone_number);
                    break;
            }

            return mediumFriendlyName;
        }

    }

    /**
     * 3 pid credentials
     */
    public static class ThreePidCreds {

        // the identity server URL (without the http://)
        public String id_server;

        // the 3 pids sid
        public String sid;

        // a secret key
        public String client_secret;
    }

    /**
     *
     */
    public static class Invite3Pid {
        /**
         * Required.
         * The hostname+port of the identity server which should be used for third party identifier lookups.
         */
        public String id_server;

        /**
         * Required.
         * The kind of address being passed in the address field, for example email.
         */
        public String medium;

        /**
         * Required.
         * The invitee's third party identifier.
         */
        public String address;
    }
}
