package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class ChatSendAsCell extends LinearLayout {

    private HeaderCell headerCell;
    private RecyclerListView listView;

    private ArrayList<TLRPC.Peer> chats;
    private int currentAccount;

    private TLRPC.Peer selectedPeer;

    private static ArrayList<TLRPC.Peer> cachedChats;
    private static long lastCacheTime;
    private static long lastCacheDid;
    private static int lastCachedAccount;
    private static int lastTopPosition = -1;
    private static int lastTopPositionOffset = 0;


    public static void resetCache() {
        cachedChats = null;
        lastTopPosition = 0;
        lastTopPositionOffset = 0;
    }

    public static void processDeletedChat(int account, long did) {
        if (lastCachedAccount != account || cachedChats == null || did > 0) {
            return;
        }
        for (int a = 0, N = cachedChats.size(); a < N; a++) {
            if (MessageObject.getPeerId(cachedChats.get(a)) == did) {
                cachedChats.remove(a);
                break;
            }
        }
        if (cachedChats.isEmpty()) {
            cachedChats = null;
        }
    }

    public interface ChatSendAsPeersCountDelegate {
        void peersCount(int count);
    }

    public interface ChatSendAsCellDelegate {
        void viewReady(ChatSendAsCell view);

        void didSelectChat(View view, TLRPC.Peer peer);

        void peersCount(int count);
    }

    public static void load(Context context, long did, AccountInstance accountInstance, boolean forceUpdatePeers, ChatSendAsPeersCountDelegate delegate) {
        if (context == null || delegate == null) {
            return;
        }
        if (!forceUpdatePeers && lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 5 * 60 * 1000) {
            delegate.peersCount(cachedChats.size());
        } else {
            TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(-did);
            accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    delegate.peersCount(res.peers.size());
                }
            }));
        }
    }

    public static void open(Context context, long did, AccountInstance accountInstance, TLRPC.Peer selectedPeer, ChatSendAsCellDelegate delegate) {
        if (context == null || delegate == null) {
            return;
        }
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 60 * 1000) {
            createView(context, accountInstance, cachedChats, selectedPeer, delegate);
            delegate.peersCount(cachedChats.size());
        } else {
            final AlertDialog progressDialog = new AlertDialog(context, 3);

            TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(-did);
            int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response != null) {
                    TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    createView(context, accountInstance, res.peers, selectedPeer, delegate);
                    delegate.peersCount(res.peers.size());
                }
            }));
            progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(500);
            } catch (Exception ignore) {

            }
        }
    }

    private static void createView(Context context, AccountInstance accountInstance, ArrayList<TLRPC.Peer> peers, TLRPC.Peer selectedPeer, ChatSendAsCell.ChatSendAsCellDelegate delegate) {
        ChatSendAsCell cell = new ChatSendAsCell(context, accountInstance, peers, selectedPeer, delegate);
        delegate.viewReady(cell);
    }

    private ChatSendAsCell(Context context, AccountInstance accountInstance, ArrayList<TLRPC.Peer> arrayList, TLRPC.Peer selectedPeer, ChatSendAsCell.ChatSendAsCellDelegate delegate) {
        super(context);
        setOrientation(VERTICAL);
        chats = new ArrayList<>(arrayList);
        this.currentAccount = accountInstance.getCurrentAccount();

        this.selectedPeer = selectedPeer;
        setWillNotDraw(false);

        headerCell = new HeaderCell(context, 23);
        headerCell.setText(LocaleController.getString("SendMessageAs", R.string.SendMessageAs));
        addView(headerCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 8));

        listView = new RecyclerListView(context);
        LinearLayoutManager manager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(manager);
        listView.setAdapter(new ChatSendAsCell.ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));

        listView.setOnItemClickListener((view, position) -> {
            delegate.didSelectChat(view, chats.get(position));
        });

        FrameLayout frameLayout = new FrameLayout(getContext());
        addView(frameLayout);

        listView.setSelectorDrawableColor(0);
        listView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        listView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(500), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), MeasureSpec.AT_MOST));
        frameLayout.addView(listView);

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow);
        shadow.getBackground().setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private float visibleShadowRange = 30;
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                float scrollDy = recyclerView.computeVerticalScrollOffset();
                float alpha = Math.min(scrollDy / visibleShadowRange, 1f);
                shadow.setAlpha(alpha);
            }
        });

        if (lastTopPosition >= 0) {
            manager.scrollToPositionWithOffset(lastTopPosition, lastTopPositionOffset);
        } else {
            int position = -1;
            for (int i = 0; i < chats.size(); i++) {
                if (MessageObject.getPeerId(selectedPeer) == MessageObject.getPeerId(chats.get(i))) {
                    position = i;
                    break;
                }
            }
            if (position >= 0) {
                manager.scrollToPositionWithOffset(position, 0, true);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (listView != null && listView.getLayoutManager() != null && listView.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
            lastTopPosition = manager.findFirstVisibleItemPosition();
            View view = manager.findViewByPosition(lastTopPosition);
            if (view != null) {
                lastTopPositionOffset = view.getTop();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(500), MeasureSpec.AT_MOST));
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new GroupCreateUserCell(context, 2, 0, false);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            long did = MessageObject.getPeerId(selectedPeer);
            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            Object object = cell.getObject();
            long id = 0;
            if (object != null) {
                if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = ((TLRPC.User) object).id;
                }
            }
            cell.setChecked(did == id, false);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            long did = MessageObject.getPeerId(chats.get(position));
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }
            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            cell.setObject(object, null, status, false);
        }
    }
}