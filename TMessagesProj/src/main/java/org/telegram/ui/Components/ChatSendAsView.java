package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatSendAsCell;

public class ChatSendAsView extends SizeNotifierFrameLayout {

    public interface ChatSendAsViewDelegate {
        void updateView();
    }

    private ActionBarPopupWindow scrimPopupWindow;
    private AccountInstance accountInstance;

    private AvatarDrawable avatarDrawable;
    private BackupImageView imageView;

    private ImageView closeButton;

    private AnimatorSet currentButtonAnimation;

    private TLRPC.TL_channel channel;
    private TLRPC.ChatFull chatFull;
    private ChatSendAsViewDelegate delegate;
    private boolean isShowView = false;

    public ChatSendAsView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, ChatSendAsViewDelegate delegate) {
        super(context);
        this.delegate = delegate;
        accountInstance = AccountInstance.getInstance(currentAccount);
        avatarDrawable = new AvatarDrawable(resourcesProvider);

        imageView = new BackupImageView(context);
        addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER, 0, 0, 0, 0));
        imageView.setRoundRadius(AndroidUtilities.dp(16));

        closeButton = new ImageView(context);
        CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), R.drawable.ic_layer_close);
        combinedDrawable.setIconSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.key_chats_actionBackground), false);
        Theme.setCombinedDrawableColor(combinedDrawable, Theme.getColor(Theme.key_chats_actionIcon), true);
        closeButton.setImageDrawable(combinedDrawable);

        closeButton.setOnClickListener(v -> setIsOpen(false, true));

        addView(closeButton, LayoutHelper.createFrame(32, 32, Gravity.CENTER, 0, 0, 0, 0));

        setIsOpen(false, false);
    }

    private void updateAvatar(long uid) {
        if (DialogObject.isUserDialog(uid)) {
            TLRPC.User user = accountInstance.getMessagesController().getUser(uid);
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);
        } else {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-uid);
            avatarDrawable.setInfo(chat);
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
    }

    private void setIsOpen(boolean isOpen, boolean animate) {
        if (animate) {
            if (currentButtonAnimation != null) {
                currentButtonAnimation.cancel();
            }
            currentButtonAnimation = new AnimatorSet();

            if (isOpen) {
                closeButton.setVisibility(View.VISIBLE);
                currentButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(closeButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(closeButton, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(closeButton, View.ALPHA, 1.0f),

                        ObjectAnimator.ofFloat(imageView, View.SCALE_X, 0.0f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 0.0f),
                        ObjectAnimator.ofFloat(imageView, View.ALPHA, 0.0f)
                );
                currentButtonAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageView.setVisibility(View.INVISIBLE);
                    }
                });

            } else{
                imageView.setVisibility(View.VISIBLE);
                currentButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(closeButton, View.SCALE_X, 0.0f),
                        ObjectAnimator.ofFloat(closeButton, View.SCALE_Y, 0.0f),
                        ObjectAnimator.ofFloat(closeButton, View.ALPHA, 0.0f),

                        ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(imageView, View.ALPHA, 1.0f)
                );
                currentButtonAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        closeButton.setVisibility(View.INVISIBLE);
                    }
                });
            }

            currentButtonAnimation.setDuration(180);
            currentButtonAnimation.start();
        } else {
            if (isOpen) {
                closeButton.setVisibility(View.VISIBLE);
                closeButton.setScaleX(1.0f);
                closeButton.setScaleY(1.0f);
                closeButton.setAlpha(1.0f);

                imageView.setVisibility( View.INVISIBLE);
                imageView.setScaleX(0.0f);
                imageView.setScaleY(0.0f);
                imageView.setAlpha(0.0f);
            } else  {
                closeButton.setVisibility(View.INVISIBLE);
                closeButton.setScaleX(0.0f);
                closeButton.setScaleY(0.0f);
                closeButton.setAlpha(0.0f);

                imageView.setVisibility(View.VISIBLE);
                imageView.setScaleX(1.0f);
                imageView.setScaleY(1.0f);
                imageView.setAlpha(1.0f);
            }
        }
    }

    public void setChannelAndFull(TLRPC.Chat chat, TLRPC.ChatFull chatFull) {
        if (this.chatFull == chatFull && this.channel == chat) {
            return;
        }
        if (chatFull != null && (chatFull.flags & 536870912) != 0 && ChatObject.isChannelForSendAs(chat)) {
            this.chatFull = chatFull;
            this.channel = (TLRPC.TL_channel) chat;
            ChatSendAsCell.load(getContext(), channel.id, accountInstance, count -> {
                isShowView = count > 1;
                if (delegate != null) {
                    delegate.updateView();
                }
            });
            updateAvatar(MessageObject.getPeerId(chatFull.default_send_as));
            imageView.setOnClickListener(view -> {
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
                ChatSendAsCell.open(getContext(), channel.id, accountInstance, chatFull.default_send_as, new ChatSendAsCell.ChatSendAsCellDelegate() {

                    @Override
                    public void viewReady(ChatSendAsCell sendAsCell) {
                        LinearLayout scrimPopupContainerLayout = new LinearLayout(getContext()) {
                            @Override
                            public boolean dispatchKeyEvent(KeyEvent event) {
                                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                                    scrimPopupWindow.dismiss();
                                }
                                return super.dispatchKeyEvent(event);
                            }
                        };
                        scrimPopupContainerLayout.setOrientation(LinearLayout.VERTICAL);

                        Drawable shadowDrawable2 = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert).mutate();
                        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
                        scrimPopupContainerLayout.setBackground(shadowDrawable2);

                        scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                            @Override
                            public void dismiss() {
                                super.dismiss();
                                setIsOpen(false, true);
                                if (scrimPopupWindow != this) {
                                    return;
                                }
                                scrimPopupWindow = null;
                            }
                        };
                        setIsOpen(true, true);

                        scrimPopupWindow.setPauseNotifications(true);
                        scrimPopupWindow.setDismissAnimationDuration(220);
                        scrimPopupWindow.setOutsideTouchable(true);
                        scrimPopupWindow.setClippingEnabled(true);
                        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                        scrimPopupWindow.setFocusable(true);
                        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
                        scrimPopupContainerLayout.addView(sendAsCell);
                        scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));

                        int keyboardHeight = ChatSendAsView.this.measureKeyboardHeight();
                        scrimPopupWindow.showAtLocation(ChatSendAsView.this, Gravity.LEFT | Gravity.BOTTOM, 0, keyboardHeight + ChatSendAsView.this.getMeasuredHeight() + AndroidUtilities.dp(16));
                    }

                    @Override
                    public void didSelectChat(TLRPC.Peer peer) {
                        if (scrimPopupWindow != null) {
                            scrimPopupWindow.dismiss();
                        }
                        TLRPC.TL_messages_saveDefaultSendAs req = new TLRPC.TL_messages_saveDefaultSendAs();
                        req.peer = MessagesController.getInputPeer(channel);
                        req.send_as = accountInstance.getMessagesController().getInputPeer(MessageObject.getPeerId(peer));
                        accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                            if (response != null) {
                                chatFull.default_send_as = peer;
                                accountInstance.getMessagesStorage().updateChatInfo(chatFull, false);
                                accountInstance.getMessagesController().putChatFull(chatFull);
                                updateAvatar(MessageObject.getPeerId(chatFull.default_send_as));
                            }
                        });
                    }
                });
            });
        } else {
            this.chatFull = null;
            this.channel = null;
            isShowView = false;
        }
    }

    public boolean isShowSendAsView() {
        return isShowView;
    }

    public void resetCache() {
        ChatSendAsCell.resetCache();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
    }
}
