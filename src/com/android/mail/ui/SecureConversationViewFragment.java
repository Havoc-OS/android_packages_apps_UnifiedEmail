/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.ui;

import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.browse.MessageFooterView;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.MessageScrollView;
import com.android.mail.browse.MessageWebView;
import com.android.mail.content.ObjectCursor;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.HashSet;

public class SecureConversationViewFragment extends AbstractConversationViewFragment implements
        MessageHeaderViewCallbacks {
    private static final String LOG_TAG = LogTag.getLogTag();
    private MessageWebView mWebView;
    private ConversationViewHeader mConversationHeaderView;
    private MessageHeaderView mMessageHeaderView;
    private MessageFooterView mMessageFooterView;
    private ConversationMessage mMessage;
    private MessageScrollView mScrollView;

    private final WebViewClient mWebViewClient = new AbstractConversationWebViewClient() {
        @Override
        public void onPageFinished(WebView view, String url) {
            if (isUserVisible()) {
                onConversationSeen();
            }

            dismissLoadingStatus();
        }
    };

    /**
     * Creates a new instance of {@link ConversationViewFragment}, initialized
     * to display a conversation with other parameters inherited/copied from an
     * existing bundle, typically one created using {@link #makeBasicArgs}.
     */
    public static SecureConversationViewFragment newInstance(Bundle existingArgs,
            Conversation conversation) {
        SecureConversationViewFragment f = new SecureConversationViewFragment();
        Bundle args = new Bundle(existingArgs);
        args.putParcelable(ARG_CONVERSATION, conversation);
        f.setArguments(args);
        return f;
    }

    /**
     * Constructor needs to be public to handle orientation changes and activity
     * lifecycle events.
     */
    public SecureConversationViewFragment() {
        super();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mConversationHeaderView.setCallbacks(this, this);
        mConversationHeaderView.setFoldersVisible(false);
        mConversationHeaderView.setSubject(mConversation.subject);
        mMessageHeaderView.initialize(mDateBuilder, this, mAddressCache);
        mMessageHeaderView.setExpandMode(MessageHeaderView.POPUP_MODE);
        mMessageHeaderView.setContactInfoSource(getContactInfoSource());
        mMessageHeaderView.setCallbacks(this);
        mMessageHeaderView.setExpandable(false);
        mMessageHeaderView.setVeiledMatcher(
                ((ControllableActivity) getActivity()).getAccountController()
                        .getVeiledAddressMatcher());
        mMessageFooterView.initialize(getLoaderManager(), getFragmentManager());
        getLoaderManager().initLoader(MESSAGE_LOADER, null, getMessageLoaderCallbacks());
        showLoadingStatus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.secure_conversation_view, container, false);
        mScrollView = (MessageScrollView) rootView.findViewById(R.id.scroll_view);
        mConversationHeaderView = (ConversationViewHeader) rootView.findViewById(R.id.conv_header);
        mMessageHeaderView = (MessageHeaderView) rootView.findViewById(R.id.message_header);
        mMessageFooterView = (MessageFooterView) rootView.findViewById(R.id.message_footer);
        instantiateProgressIndicators(rootView);
        mWebView = (MessageWebView) rootView.findViewById(R.id.webview);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setFocusable(false);
        final WebSettings settings = mWebView.getSettings();

        settings.setJavaScriptEnabled(false);
        settings.setLayoutAlgorithm(LayoutAlgorithm.NORMAL);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        mScrollView.setInnerScrollableView(mWebView);

        return rootView;
    }

    @Override
    protected WebView getWebView() {
        return mWebView;
    }

    @Override
    protected void markUnread() {
        super.markUnread();
        // Ignore unsafe calls made after a fragment is detached from an activity
        final ControllableActivity activity = (ControllableActivity) getActivity();
        if (activity == null || mConversation == null || mMessage == null) {
            LogUtils.w(LOG_TAG, "ignoring markUnread for conv=%s",
                    mConversation != null ? mConversation.id : 0);
            return;
        }
        final HashSet<Uri> uris = new HashSet<Uri>();
        uris.add(mMessage.uri);
        activity.getConversationUpdater().markConversationMessagesUnread(mConversation, uris,
                mViewState.getConversationInfo());
    }

    @Override
    public void onAccountChanged(Account newAccount, Account oldAccount) {
        // Do nothing.
    }

    @Override
    public void onConversationViewHeaderHeightChange(int newHeight) {
        // Do nothing.
    }

    @Override
    public void onUserVisibleHintChanged() {
        if (mActivity == null) {
            return;
        }
        if (isUserVisible()) {
            onConversationSeen();
        }
    }

    @Override
    public void setMessageSpacerHeight(MessageHeaderItem item, int newSpacerHeight) {
        // Do nothing.
    }

    @Override
    public void setMessageExpanded(MessageHeaderItem item, int newSpacerHeight) {
        // Do nothing.
    }

    @Override
    public void setMessageDetailsExpanded(MessageHeaderItem i, boolean expanded, int heightbefore) {
        // Do nothing.
    }

    @Override
    public void showExternalResources(final Message msg) {
        mWebView.getSettings().setBlockNetworkImage(false);
    }

    @Override
    public void showExternalResources(final String rawSenderAddress) {
        mWebView.getSettings().setBlockNetworkImage(false);
    }

    @Override
    public boolean supportsMessageTransforms() {
        return false;
    }

    @Override
    public String getMessageTransforms(final Message msg) {
        return null;
    }

    @Override
    protected void onMessageCursorLoadFinished(Loader<ObjectCursor<ConversationMessage>> loader,
            MessageCursor newCursor, MessageCursor oldCursor) {
        // ignore cursors that are still loading results
        if (newCursor == null || !newCursor.isLoaded()) {
            LogUtils.i(LOG_TAG, "CONV RENDER: existing cursor is null, rendering from scratch");
            return;
        }
        if (mActivity == null || mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }
        renderMessageBodies(newCursor);
    }

    /**
     * Populate the adapter with overlay views (message headers, super-collapsed
     * blocks, a conversation header), and return an HTML document with spacer
     * divs inserted for all overlays.
     */
    private void renderMessageBodies(MessageCursor messageCursor) {
        if (!messageCursor.moveToFirst()) {
            LogUtils.e(LOG_TAG, "unable to open message cursor");
            return;
        }
        final ConversationMessage m = messageCursor.getMessage();
        mMessage = messageCursor.getMessage();
        mWebView.getSettings().setBlockNetworkImage(!mMessage.alwaysShowImages);
        mWebView.loadDataWithBaseURL(mBaseUri, m.getBodyAsHtml(), "text/html", "utf-8", null);
        final ConversationViewAdapter adapter = new ConversationViewAdapter(mActivity, null, null,
                null, null, null, null, null, null);
        final MessageHeaderItem item = adapter.newMessageHeaderItem(mMessage, true,
                mMessage.alwaysShowImages);
        mMessageHeaderView.bind(item, false);
        if (mMessage.hasAttachments) {
            mMessageFooterView.setVisibility(View.VISIBLE);
            mMessageFooterView.bind(item, false);
        }
    }

    @Override
    public void onConversationUpdated(Conversation conv) {
        final ConversationViewHeader headerView = mConversationHeaderView;
        if (headerView != null) {
            headerView.onConversationUpdated(conv);
            headerView.setSubject(conv.subject);
        }
    }

}
