/* 
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package org.matrix.androidsdk.rest.client;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.api.RoomsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReportContentParams;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.sync.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsRestClient extends RestClient<RoomsApi> {
    private static final String LOG_TAG = RoomsRestClient.class.getSimpleName();

    public static final int DEFAULT_MESSAGES_PAGINATION_LIMIT = 30;

    // read marker field names
    private static final String READ_MARKER_FULLY_READ = "m.fully_read";
    private static final String READ_MARKER_READ = "m.read";

    /**
     * {@inheritDoc}
     */
    public RoomsRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, RoomsApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Send a message to room
     *
     * @param transactionId the unique transaction id (it should avoid duplicated messages)
     * @param roomId        the room id
     * @param message       the message
     * @param callback      the callback containing the created event if successful
     */
    public void sendMessage(final String transactionId, final String roomId, final Message message, final ApiCallback<Event> callback) {
        // privacy
        // final String description = "SendMessage : roomId " + roomId + " - message " + message.body;
        final String description = "SendMessage : roomId " + roomId;

        try {
            // the messages have their dedicated method in MXSession to be resent if there is no available network
            mApi.sendMessage(transactionId, roomId, message, new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    sendMessage(transactionId, roomId, message, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Send an event to a room.
     *
     * @param transactionId the unique transaction id (it should avoid duplicated messages)
     * @param roomId        the room id
     * @param eventType     the type of event
     * @param content       the event content
     * @param callback      the callback containing the created event if successful
     */
    public void sendEventToRoom(final String transactionId, final String roomId, final String eventType, final JsonObject content, final ApiCallback<Event> callback) {
        // privacy
        //final String description = "sendEvent : roomId " + roomId + " - eventType " + eventType + " content " + content;
        final String description = "sendEvent : roomId " + roomId + " - eventType " + eventType;

        try {
            // do not retry the call invite
            // it might trigger weird behaviour on flaggy networks
            if (!TextUtils.equals(eventType, Event.EVENT_TYPE_CALL_INVITE)) {
                mApi.send(transactionId, roomId, eventType, content, new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        sendEventToRoom(transactionId, roomId, eventType, content, callback);
                    }
                }));
            } else {
                mApi.send(transactionId, roomId, eventType, content, new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, null));
            }
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Get a limited amount of messages, for the given room starting from the given token. The amount of message is set to {@link #DEFAULT_MESSAGES_PAGINATION_LIMIT}.
     *
     * @param roomId    the room id
     * @param fromToken the token identifying the message to start from
     * @param direction the direction
     * @param limit     the maximum number of messages to retrieve.
     * @param callback  the callback called with the response. Messages will be returned in reverse order.
     */
    public void getRoomMessagesFrom(final String roomId, final String fromToken, final EventTimeline.Direction direction, final int limit, final ApiCallback<TokensChunkResponse<Event>> callback) {
        final String description = "messagesFrom : roomId " + roomId + " fromToken " + fromToken + "with direction " + direction + " with limit " + limit;

        try {
            mApi.getRoomMessagesFrom(roomId, (direction == EventTimeline.Direction.BACKWARDS) ? "b" : "f", fromToken, limit, new RestAdapterCallback<TokensChunkResponse<Event>>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getRoomMessagesFrom(roomId, fromToken, direction, limit, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Invite a user to a room.
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param callback the async callback
     */
    public void inviteUserToRoom(final String roomId, final String userId, final ApiCallback<Void> callback) {
        final String description = "inviteToRoom : roomId " + roomId + " userId " + userId;

        User user = new User();
        user.user_id = userId;

        try {
            mApi.invite(roomId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    inviteUserToRoom(roomId, userId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Invite a user by his email address to a room.
     *
     * @param roomId   the room id
     * @param email    the email
     * @param callback the async callback
     */
    public void inviteByEmailToRoom(final String roomId, final String email, final ApiCallback<Void> callback) {
        inviteThreePidToRoom("email", email, roomId, callback);
    }

    /**
     * Invite an user from a 3Pids.
     *
     * @param medium   the medium
     * @param address  the address
     * @param roomId   the room id
     * @param callback the async callback
     */
    private void inviteThreePidToRoom(final String medium, final String address, final String roomId, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "inviteThreePidToRoom : medium " + medium + " address " + address + " roomId " + roomId;
        final String description = "inviteThreePidToRoom : medium " + medium + " roomId " + roomId;

        // This request must not have the protocol part
        String identityServer = mHsConfig.getIdentityServerUri().toString();

        if (identityServer.startsWith("http://")) {
            identityServer = identityServer.substring("http://".length());
        } else if (identityServer.startsWith("https://")) {
            identityServer = identityServer.substring("https://".length());
        }

        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("id_server", identityServer);
        parameters.put("medium", medium);
        parameters.put("address", address);

        try {
            mApi.invite(roomId, parameters, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    inviteThreePidToRoom(medium, address, roomId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Join a room by its roomAlias or its roomId.
     *
     * @param roomIdOrAlias the room id or the room alias
     * @param callback      the async callback
     */
    public void joinRoom(final String roomIdOrAlias, final ApiCallback<RoomResponse> callback) {
        joinRoom(roomIdOrAlias, null, callback);
    }

    /**
     * Join a room by its roomAlias or its roomId with some parameters.
     *
     * @param roomIdOrAlias the room id or the room alias
     * @param params        the joining parameters.
     * @param callback      the async callback
     */
    public void joinRoom(final String roomIdOrAlias, final HashMap<String, Object> params, final ApiCallback<RoomResponse> callback) {
        final String description = "joinRoom : roomId " + roomIdOrAlias;

        try {
            mApi.joinRoomByAliasOrId(roomIdOrAlias, (null == params) ? new HashMap<String, Object>() : params, new RestAdapterCallback<RoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    joinRoom(roomIdOrAlias, params, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Leave a room.
     *
     * @param roomId   the room id
     * @param callback the async callback
     */
    public void leaveRoom(final String roomId, final ApiCallback<Void> callback) {
        final String description = "leaveRoom : roomId " + roomId;

        try {
            mApi.leave(roomId, new JsonObject(), new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    leaveRoom(roomId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Forget a room.
     *
     * @param roomId   the room id
     * @param callback the async callback
     */
    public void forgetRoom(final String roomId, final ApiCallback<Void> callback) {
        final String description = "forgetRoom : roomId " + roomId;

        try {
            mApi.forget(roomId, new JsonObject(), new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    leaveRoom(roomId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Kick a user from a room.
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param callback the async callback
     */
    public void kickFromRoom(final String roomId, final String userId, final ApiCallback<Void> callback) {
        final String description = "kickFromRoom : roomId " + roomId + " userId " + userId;

        // Kicking is done by posting that the user is now in a "leave" state
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_LEAVE;

        try {
            mApi.updateRoomMember(roomId, userId, member, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    kickFromRoom(roomId, userId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Ban a user from a room.
     *
     * @param roomId   the room id
     * @param user     the banned user object (userId and reason for ban)
     * @param callback the async callback
     */
    public void banFromRoom(final String roomId, final BannedUser user, final ApiCallback<Void> callback) {
        final String description = "banFromRoom : roomId " + roomId + " userId " + user.userId;

        try {
            mApi.ban(roomId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    banFromRoom(roomId, user, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Unban an user from a room.
     *
     * @param roomId   the room id
     * @param user     the banned user (userId)
     * @param callback the async callback
     */
    public void unbanFromRoom(final String roomId, final BannedUser user, final ApiCallback<Void> callback) {
        final String description = "Unban : roomId " + roomId + " userId " + user.userId;

        try {
            mApi.unban(roomId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    unbanFromRoom(roomId, user, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Create a new room.
     *
     * @param params the room creation parameters
     * @param callback   the async callback
     */
    public void createRoom(final CreateRoomParams params, final ApiCallback<CreateRoomResponse> callback) {
        // privacy
        //final String description = "createRoom : name " + name + " topic " + topic;
        final String description = "createRoom";

        try {
            mApi.createRoom(params, new RestAdapterCallback<CreateRoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    createRoom(params, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Perform an initial sync on the room
     *
     * @param roomId   the room id
     * @param callback the async callback
     */
    public void initialSync(final String roomId, final ApiCallback<RoomResponse> callback) {
        final String description = "initialSync : roomId " + roomId;

        try {
            mApi.initialSync(roomId, DEFAULT_MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<RoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    initialSync(roomId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Get the context surrounding an event.
     *
     * @param roomId   the room id
     * @param eventId  the event Id
     * @param limit    the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    public void getContextOfEvent(final String roomId, final String eventId, final int limit, final ApiCallback<EventContext> callback) {
        final String description = "getContextOfEvent : roomId " + roomId + " eventId " + eventId + " limit " + limit;

        try {
            mApi.getContextOfEvent(roomId, eventId, limit, new RestAdapterCallback<EventContext>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getContextOfEvent(roomId, eventId, limit, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the room name.
     *
     * @param roomId   the room id
     * @param name     the room name
     * @param callback the async callback
     */
    public void updateRoomName(final String roomId, final String name, final ApiCallback<Void> callback) {
        final String description = "updateName : roomId " + roomId + " name " + name;

        RoomState roomState = new RoomState();
        roomState.name = name;

        try {
            mApi.setRoomName(roomId, roomState, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateRoomName(roomId, name, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the room name.
     *
     * @param roomId         the room id
     * @param canonicalAlias the canonical alias
     * @param callback       the async callback
     */
    public void updateCanonicalAlias(final String roomId, final String canonicalAlias, final ApiCallback<Void> callback) {
        final String description = "updateCanonicalAlias : roomId " + roomId + " canonicalAlias " + canonicalAlias;

        RoomState roomState = new RoomState();
        roomState.alias = canonicalAlias;

        try {
            mApi.setCanonicalAlias(roomId, roomState, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateCanonicalAlias(roomId, canonicalAlias, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the room name.
     *
     * @param roomId      the room id
     * @param aVisibility the visibility
     * @param callback    the async callback
     */
    public void updateHistoryVisibility(final String roomId, final String aVisibility, final ApiCallback<Void> callback) {
        final String description = "updateHistoryVisibility : roomId " + roomId + " visibility " + aVisibility;

        RoomState roomState = new RoomState();
        roomState.history_visibility = aVisibility;

        try {
            mApi.setHistoryVisibility(roomId, roomState, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateHistoryVisibility(roomId, aVisibility, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the directory visibility of the room.
     *
     * @param aRoomId              the room id
     * @param aDirectoryVisibility the visibility of the room in the directory list
     * @param callback             the async callback response
     */
    public void updateDirectoryVisibility(final String aRoomId, final String aDirectoryVisibility, final ApiCallback<Void> callback) {
        final String description = "updateRoomDirectoryVisibility : roomId=" + aRoomId + " visibility=" + aDirectoryVisibility;

        RoomState roomState = new RoomState();
        roomState.visibility = aDirectoryVisibility;

        try {
            mApi.setRoomDirectoryVisibility(aRoomId, roomState, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateDirectoryVisibility(aRoomId, aDirectoryVisibility, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }


    /**
     * Get the directory visibility of the room (see {@link #updateDirectoryVisibility(String, String, ApiCallback)}).
     *
     * @param aRoomId  the room ID
     * @param callback on success callback containing a RoomState object populated with the directory visibility
     */
    public void getDirectoryVisibility(final String aRoomId, final ApiCallback<RoomState> callback) {
        final String description = "getRoomDirectoryVisibility userId=" + aRoomId;

        try {
            mApi.getRoomDirectoryVisibility(aRoomId, new RestAdapterCallback<RoomState>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getDirectoryVisibility(aRoomId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the room topic.
     *
     * @param roomId   the room id
     * @param topic    the room topic
     * @param callback the async callback
     */
    public void updateTopic(final String roomId, final String topic, final ApiCallback<Void> callback) {
        final String description = "updateTopic : roomId " + roomId + " topic " + topic;

        RoomState roomState = new RoomState();
        roomState.topic = topic;

        try {
            mApi.setRoomTopic(roomId, roomState, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateTopic(roomId, topic, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Redact an event.
     *
     * @param roomId   the room id
     * @param eventId  the event id
     * @param callback the callback containing the created event if successful
     */
    public void redactEvent(final String roomId, final String eventId, final ApiCallback<Event> callback) {
        final String description = "redactEvent : roomId " + roomId + " eventId " + eventId;

        try {
            mApi.redactEvent(roomId, eventId, new JsonObject(), new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    redactEvent(roomId, eventId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Report an event.
     *
     * @param roomId   the room id
     * @param eventId  the event id
     * @param score    the metric to let the user rate the severity of the abuse. It ranges from -100 “most offensive” to 0 “inoffensive”
     * @param reason   the reason
     * @param callback the callback containing the created event if successful
     */
    public void reportEvent(final String roomId, final String eventId, final int score, final String reason, final ApiCallback<Void> callback) {
        final String description = "report : roomId " + roomId + " eventId " + eventId;

        ReportContentParams content = new ReportContentParams();

        ArrayList<Integer> scores = new ArrayList<>();
        scores.add(score);

        content.score = scores;
        content.reason = reason;

        try {
            mApi.reportEvent(roomId, eventId, content, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    reportEvent(roomId, eventId, score, reason, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the power levels.
     *
     * @param roomId      the room id
     * @param powerLevels the new powerLevels
     * @param callback    the async callback
     */
    public void updatePowerLevels(final String roomId, final PowerLevels powerLevels, final ApiCallback<Void> callback) {
        final String description = "updatePowerLevels : roomId " + roomId + " powerLevels " + powerLevels;

        try {
            mApi.setPowerLevels(roomId, powerLevels, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updatePowerLevels(roomId, powerLevels, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Send a state events.
     *
     * @param roomId    the dedicated room id
     * @param eventType the event type
     * @param stateKey  the state key
     * @param params    the put parameters
     * @param callback  the asynchronous callback
     */
    public void sendStateEvent(final String roomId, final String eventType, @Nullable final String stateKey, final Map<String, Object> params, final ApiCallback<Void> callback) {
        final String description = "sendStateEvent : roomId " + roomId + " - eventType " + eventType;

        try {
            if (null != stateKey) {
                mApi.sendStateEvent(roomId, eventType, stateKey, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        sendStateEvent(roomId, eventType, stateKey, params, callback);
                    }
                }));
            } else {
                mApi.sendStateEvent(roomId, eventType, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        sendStateEvent(roomId, eventType, null, params, callback);
                    }
                }));
            }
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Looks up the contents of a state event in a room
     *
     * @param roomId    the room id
     * @param eventType the event type
     * @param callback  the asynchronous callback
     */
    public void getStateEvent(final String roomId, final String eventType, final ApiCallback<JsonElement> callback) {
        final String description = "getStateEvent : roomId " + roomId + " eventId " + eventType;

        try {
            mApi.getStateEvent(roomId, eventType, new RestAdapterCallback<JsonElement>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getStateEvent(roomId, eventType, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Looks up the contents of a state event in a room
     *
     * @param roomId    the room id
     * @param eventType the event type
     * @param stateKey  the key of the state to look up
     * @param callback  the asynchronous callback
     */
    public void getStateEvent(final String roomId, final String eventType, final String stateKey, final ApiCallback<JsonElement> callback) {
        final String description = "getStateEvent : roomId " + roomId + " eventId " + eventType + " stateKey " + stateKey;

        try {
            mApi.getStateEvent(roomId, eventType, stateKey, new RestAdapterCallback<JsonElement>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getStateEvent(roomId, eventType, stateKey, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * send typing notification.
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param isTyping true if the user is typing
     * @param timeout  the typing event timeout
     * @param callback the asynchronous callback
     */
    public void sendTypingNotification(String roomId, String userId, boolean isTyping, int timeout, ApiCallback<Void> callback) {
        final String description = "sendTypingNotification : roomId " + roomId + " isTyping " + isTyping;

        Typing typing = new Typing();
        typing.typing = isTyping;

        if (-1 != timeout) {
            typing.timeout = timeout;
        }

        try {
            // never resend typing on network error
            mApi.setTypingNotification(roomId, userId, typing, new RestAdapterCallback<Void>(description, null, callback, null));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the room avatar url.
     *
     * @param roomId    the room id
     * @param avatarUrl canonical alias
     * @param callback  the async callback
     */
    public void updateAvatarUrl(final String roomId, final String avatarUrl, final ApiCallback<Void> callback) {
        final String description = "updateAvatarUrl : roomId " + roomId + " avatarUrl " + avatarUrl;

        HashMap<String, String> params = new HashMap<>();
        params.put("url", avatarUrl);

        try {
            mApi.setRoomAvatarUrl(roomId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateAvatarUrl(roomId, avatarUrl, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Send a read markers.
     *
     * @param roomId    the room id
     * @param rmEventId the read marker event Id
     * @param rrEventId the read receipt event Id
     * @param callback  the callback
     */
    public void sendReadMarker(final String roomId, final String rmEventId, final String rrEventId, final ApiCallback<Void> callback) {
        final String description = "sendReadMarker : roomId " + roomId + " - rmEventId " + rmEventId + " -- rrEventId " + rrEventId;
        Map<String, String> params = new HashMap<>();

        if (!TextUtils.isEmpty(rmEventId)) {
            params.put(READ_MARKER_FULLY_READ, rmEventId);
        }

        if (!TextUtils.isEmpty(rrEventId)) {
            params.put(READ_MARKER_READ, rrEventId);
        }

        try {
            mApi.sendReadMarker(roomId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, true, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    sendReadMarker(roomId, rmEventId, rrEventId, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Add a tag to a room.
     * Use this method to update the order of an existing tag.
     *
     * @param roomId   the roomId
     * @param tag      the new tag to add to the room.
     * @param order    the order.
     * @param callback the operation callback
     */
    public void addTag(final String roomId, final String tag, final Double order, final ApiCallback<Void> callback) {
        final String description = "addTag : roomId " + roomId + " - tag " + tag + " - order " + order;

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("order", order);

        try {
            mApi.addTag(mCredentials.userId, roomId, tag, hashMap, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    addTag(roomId, tag, order, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Remove a tag to a room.
     *
     * @param roomId   the roomId
     * @param tag      the new tag to add to the room.
     * @param callback the operation callback
     */
    public void removeTag(final String roomId, final String tag, final ApiCallback<Void> callback) {
        final String description = "removeTag : roomId " + roomId + " - tag " + tag;

        try {
            mApi.removeTag(mCredentials.userId, roomId, tag, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    removeTag(roomId, tag, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Get the room ID corresponding to this room alias.
     *
     * @param roomAlias the room alias.
     * @param callback  the operation callback
     */
    public void getRoomIdByAlias(final String roomAlias, final ApiCallback<RoomAliasDescription> callback) {
        final String description = "getRoomIdByAlias : " + roomAlias;

        try {
            mApi.getRoomIdByAlias(roomAlias, new RestAdapterCallback<RoomAliasDescription>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getRoomIdByAlias(roomAlias, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Set the room ID corresponding to a room alias.
     *
     * @param roomId    the room id.
     * @param roomAlias the room alias.
     * @param callback  the operation callback
     */
    public void setRoomIdByAlias(final String roomId, final String roomAlias, final ApiCallback<Void> callback) {
        final String description = "setRoomIdByAlias : roomAlias " + roomAlias + " - roomId : " + roomId;

        RoomAliasDescription roomAliasDescription = new RoomAliasDescription();
        roomAliasDescription.room_id = roomId;

        try {
            mApi.setRoomIdByAlias(roomAlias, roomAliasDescription, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    setRoomIdByAlias(roomId, roomAlias, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Remove the room alias.
     *
     * @param roomAlias the room alias.
     * @param callback  the room alias description
     */
    public void removeRoomAlias(final String roomAlias, final ApiCallback<Void> callback) {
        final String description = "removeRoomAlias : " + roomAlias;

        try {
            mApi.removeRoomAlias(roomAlias, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    removeRoomAlias(roomAlias, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the join rule of the room.
     * To make the room private, the aJoinRule must be set to {@link RoomState#JOIN_RULE_INVITE}.
     *
     * @param aRoomId   the room id
     * @param aJoinRule the join rule: {@link RoomState#JOIN_RULE_PUBLIC} or {@link RoomState#JOIN_RULE_INVITE}
     * @param callback  the async callback response
     */
    public void updateJoinRules(final String aRoomId, final String aJoinRule, final ApiCallback<Void> callback) {
        final String description = "updateJoinRules : roomId=" + aRoomId + " rule=" + aJoinRule;

        // build RoomState as input parameter
        RoomState roomStateParam = new RoomState();
        roomStateParam.join_rule = aJoinRule;

        try {
            mApi.setJoinRules(aRoomId, roomStateParam, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateJoinRules(aRoomId, aJoinRule, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }

    }

    /**
     * Update the guest access rule of the room.
     * To deny guest access to the room, aGuestAccessRule must be set to {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     *
     * @param aRoomId          the room id
     * @param aGuestAccessRule the guest access rule: {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     * @param callback         the async callback response
     */
    public void updateGuestAccess(final String aRoomId, final String aGuestAccessRule, final ApiCallback<Void> callback) {
        final String description = "updateGuestAccess : roomId=" + aRoomId + " rule=" + aGuestAccessRule;

        // build RoomState as input parameter
        RoomState roomStateParam = new RoomState();
        roomStateParam.guest_access = aGuestAccessRule;

        try {
            mApi.setGuestAccess(aRoomId, roomStateParam, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateGuestAccess(aRoomId, aGuestAccessRule, callback);
                }
            }));
        }  catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }
}
