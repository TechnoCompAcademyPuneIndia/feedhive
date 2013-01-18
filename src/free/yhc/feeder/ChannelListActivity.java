/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.util.Calendar;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import free.yhc.feeder.LookAndFeel.ConfirmDialogAction;
import free.yhc.feeder.LookAndFeel.EditTextDialogAction;
import free.yhc.feeder.db.ColumnChannel;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.BGTaskUpdateChannel;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UIPolicy;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.UsageReport;
import free.yhc.feeder.model.Utils;

public class ChannelListActivity extends FragmentActivity implements
ActionBar.TabListener,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ChannelListActivity.class);

    // Request codes.
    private static final int REQC_PICK_PREDEFINED_CHANNEL  = 1;

    private final UIPolicy      mUip = UIPolicy.get();
    private final DBPolicy      mDbp = DBPolicy.get();
    private final RTTask        mRtt = RTTask.get();
    private final UsageReport   mUr  = UsageReport.get();
    private final LookAndFeel   mLnf = LookAndFeel.get();

    private ActionBar                   mAb             = null;
    private ViewPager                   mPager          = null;
    private ChannelListFragment         mContextMenuOwner = null;
    private DBWatcher                   mDbWatcher      = null;

    private final ViewPager.OnPageChangeListener mPCListener = new OnPageViewChange();

    private static class TabTag {
        long         categoryid;
    }

    private static class DBWatcher implements DB.OnDBUpdateListener {
        // NOTE
        // Comparing with "ChannelListFragment" and "ItemListActivity", initial value of
        //   _mCategoryTableUpdated is 'false'
        // Why?
        // Please see 'onResume'.
        // Even if this looses consistency... but... fair enough.
        private boolean _mCategoryTableUpdated = false;

        void
        register() {
            DBPolicy.get().registerUpdateListener(this, DB.UpdateType.CATEGORY_TABLE.flag());
        }

        void
        unregister() {
            DBPolicy.get().unregisterUpdateListener(this);
        }

        void
        reset() {
            _mCategoryTableUpdated = false;
        }

        boolean
        isCategoryTableUpdated() {
            return _mCategoryTableUpdated;
        }

        @Override
        protected void
        finalize() throws Throwable {
            super.finalize();
            unregister();
        }

        @Override
        public void
        onDbUpdate(DB.UpdateType type, Object arg0, Object arg1) {
            if (DB.UpdateType.CATEGORY_TABLE == type)
                _mCategoryTableUpdated = true;
            else
                eAssert(false);
        }
    }

    private class OnPageViewChange implements ViewPager.OnPageChangeListener {
        @Override
        public void
        onPageSelected(int arg0) {
            if (DBG) P.v("OnPageViewChange : onPageSelected : " + arg0);
            mAb.setSelectedNavigationItem(arg0);
        }

        @Override
        public void
        onPageScrolled(int arg0, float arg1, int arg2) { }

        @Override
        public void
        onPageScrollStateChanged(int arg0) {
            if (DBG) P.v("OnPageViewChange : onPageScrollStateChanged : " + arg0);
            // Fragment is changed. So, context menu is no more valid.
            ChannelListActivity.this.closeContextMenu();
        }
    }

    private class DeleteAllDnfilesWorker extends DiagAsyncTask.Worker {
        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            Cursor c = mDbp.queryChannel(ColumnChannel.ID);
            if (!c.moveToFirst()) {
                c.close();
                return Err.NO_ERR;
            }

            boolean bOk = true;
            do {
                if (!mUip.cleanChannelDir(c.getLong(0)))
                    bOk = false;
            } while (c.moveToNext());
            return bOk? Err.NO_ERR: Err.IO_FILE;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR != result)
                mLnf.showTextToast(ChannelListActivity.this, R.string.delete_all_downloaded_file_errmsg);
        }
    }

    private void
    restartThisActivity() {
        // category table is changed outside of this activity.
        // restarting is required!!
        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivity(intent);
        finish();
    }

    private TabTag
    getTag(Tab tab) {
        return (TabTag)tab.getTag();
    }

    private ChannelListPagerAdapter
    getPagerAdapter() {
        if (null != mPager)
            return (ChannelListPagerAdapter)mPager.getAdapter();
        return null;
    }

    private void
    selectDefaultAsSelected() {
        // 0 is index of default tab
        mAb.setSelectedNavigationItem(0);
    }

    private long
    getCategoryId(Tab tab) {
        return getTag(tab).categoryid;
    }

    private long
    getCurrentCategoryId() {
        return getPagerAdapter().getPrimaryFragment().getCategoryId();
    }

    private Tab
    addCategory(Feed.Category cat) {
        String text;
        if (mDbp.isDefaultCategoryId(cat.id)
           && !Utils.isValidValue(mDbp.getCategoryName(cat.id)))
            text = getResources().getText(R.string.default_category_name).toString();
        else
            text = cat.name;

        // Add new tab to action bar
        Tab tab = mAb.newTab()
                    .setText(text)
                    .setTag(cat.id)
                    .setTabListener(this);

        TabTag tag = new TabTag();
        tag.categoryid = cat.id;

        tab.setTag(tag);
        mAb.addTab(tab, false);
        if (null != getPagerAdapter())
            getPagerAdapter().newCategoryAdded(cat.id);
        return tab;
    }

    /**
     * All channels belonging to this category will be moved to default category.
     * @param categoryid
     */
    private void
    deleteCategory(long categoryid) {
        mDbp.deleteCategory(categoryid);

        // NOTE
        // PagerView's working mechanism is NOT GOOD for deleting item in the middle.
        // So, just restart channel list activity...to reload all!!!
        restartThisActivity();
        return;
        /*
        Tab curTab = mAb.getSelectedTab();
        mAb.removeTab(curTab);
        if (null != getPagerAdapter())
            getPagerAdapter().categoryDeleted(categoryid);
        selectDefaultAsSelected();
        */
    }

    private String
    getTabText(Tab tab) {
        return tab.getText().toString();
    }

    /**
     * Add channel to current selected category.
     * List will be scrolled to newly added channel.
     * @param url
     */
    private void
    addChannel(String url, String iconurl) {
        eAssert(url != null);
        url = Utils.removeTrailingSlash(url);

        long cid = -1;
        try {
            cid = mDbp.insertNewChannel(getCurrentCategoryId(), url);
        } catch (FeederException e) {
            mLnf.showTextToast(this, e.getError().getMsgId());
            return;
        }

        // full update for this newly inserted channel
        BGTaskUpdateChannel task;
        if (Utils.isValidValue(iconurl))
            task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid, iconurl));
        else
            task = new BGTaskUpdateChannel(new BGTaskUpdateChannel.Arg(cid));

        mRtt.register(cid, RTTask.Action.UPDATE, task);
        mRtt.start(cid, RTTask.Action.UPDATE);
        ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());

        getPagerAdapter().getPrimaryFragment().refreshListAsync();
    }


    private void
    onOpt_addChannel_youtubeEditDiag(final MenuItem item) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {}
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                switch (item.getItemId()) {
                case R.id.uploader:
                    addChannel(Utils.buildYoutubeFeedUrl_uploader(edit.getText().toString()), null);
                    break;
                case R.id.search:
                    addChannel(Utils.buildYoutubeFeedUrl_search(edit.getText().toString()), null);
                    break;
                default:
                    eAssert(false);
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, item.getTitle(), action).show();
    }

    private void
    onOpt_addChannel_youtube(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel_youtube, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                onOpt_addChannel_youtubeEditDiag(item);
                return true;
            }
        });
        popup.show();
    }


    private void
    onOpt_addChannel_url(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                // start edit box with 'http://'
                final String prefix = "http://";
                edit.setText(prefix);
                edit.setSelection(prefix.length());
            }

            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String url = edit.getText().toString();
                if (!url.matches("http\\:\\/\\/\\s*")) {
                    addChannel(url, null);
                    mUr.storeUsageReport("URL : " + url + "\n");
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.channel_url, action).show();
    }

    private void
    onOpt_addChannel_predefined(View anchor) {
        Intent intent = new Intent(this, PredefinedChannelActivity.class);
        intent.putExtra("category", getCurrentCategoryId());
        startActivityForResult(intent, REQC_PICK_PREDEFINED_CHANNEL);
    }

    private void
    onOpt_addChannel(final View anchor) {
        if (0 == mAb.getNavigationItemCount()) {
            eAssert(false);
            return;
        }

        if (0 > mAb.getSelectedNavigationIndex()) {
            mLnf.showTextToast(ChannelListActivity.this, R.string.warn_select_category_to_add);
            return;
        }

        if (!Utils.isNetworkAvailable()) {
            // TODO Handling error
            mLnf.showTextToast(ChannelListActivity.this, R.string.warn_network_unavailable);
            return;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.predefined:
                    onOpt_addChannel_predefined(anchor);
                    break;
                case R.id.url:
                    onOpt_addChannel_url(anchor);
                    break;
                case R.id.youtube:
                    onOpt_addChannel_youtube(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_category_add(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    mLnf.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    Feed.Category cat = new Feed.Category(name);
                    if (0 > mDbp.insertCategory(cat))
                        mLnf.showTextToast(ChannelListActivity.this, R.string.warn_add_category);
                    else {
                        eAssert(cat.id >= 0);
                        addCategory(cat);
                    }
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.add_category, action).show();
    }

    private void
    onOpt_category_rename(final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    mLnf.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    mAb.getSelectedTab().setText(name);
                    mDbp.updateCategory(getCurrentCategoryId(), name);
                }
            }
        };
        mLnf.buildOneLineEditTextDialog(this, R.string.rename_category, action).show();
    }

    private void
    onOpt_category_delete(final View anchor) {
        final long categoryid = getCurrentCategoryId();
        if (DBG) P.v("onOpt_category_delete : category(" + categoryid + ")");
        if (mDbp.isDefaultCategoryId(categoryid)) {
            mLnf.showTextToast(this, R.string.warn_delete_default_category);
            return;
        }

        // 0 should be default category index!
        eAssert(mAb.getSelectedNavigationIndex() > 0);

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                deleteCategory(categoryid);
            }
        };
        mLnf.buildConfirmDialog(this, R.string.delete_category, R.string.delete_category_msg, action).show();
    }

    private void
    onOpt_category(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_category, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.add:
                    onOpt_category_add(anchor);
                    break;
                case R.id.rename:
                    onOpt_category_rename(anchor);
                    break;
                case R.id.delete:
                    onOpt_category_delete(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_itemsAll(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_ALL);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }

    private void
    onOpt_itemsCategory(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_CATEGORY);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        intent.putExtra("categoryid", getCurrentCategoryId());
        startActivity(intent);
    }

    private void
    onOpt_itemsFavorite(final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_FAVORITE);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }


    private void
    onOpt_management_deleteAllDnFiles(final View anchor) {
        // check constraints
        if (mRtt.getItemsDownloading().length > 0) {
            mLnf.showTextToast(ChannelListActivity.this, R.string.del_dnfiles_not_allowed_msg);
            return;
        }

        ConfirmDialogAction action = new ConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                DiagAsyncTask task = new DiagAsyncTask(ChannelListActivity.this,
                                                       new DeleteAllDnfilesWorker(),
                                                       DiagAsyncTask.Style.SPIN,
                                                       R.string.delete_all_downloaded_file);
                task.run();
            }
        };

        mLnf.buildConfirmDialog(this,
                                       R.string.delete_all_downloaded_file,
                                       R.string.delete_all_downloaded_file_msg,
                                       action)
            .show();
    }

    private void
    onOpt_management_feedbackOpinion(final View anchor) {
        if (!Utils.isNetworkAvailable()) {
            mLnf.showTextToast(this, R.string.warn_network_unavailable);
            return;
        }

        if (!mUr.sendFeedbackReportMain(this))
            mLnf.showTextToast(this, R.string.warn_find_email_app);
    }

    private void
    onOpt_management_dbManage(final View anchor) {
        Intent intent = new Intent(this, DBManagerActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_management(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_management, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.media_delete_all:
                    onOpt_management_deleteAllDnFiles(anchor);
                    break;
                case R.id.feedback_opinion:
                    onOpt_management_feedbackOpinion(anchor);
                    break;
                case R.id.db_manage:
                    onOpt_management_dbManage(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_setting(final View anchor) {
        Intent intent = new Intent(this, FeederPreferenceActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_information(final View anchor) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(getPackageName(), 0);
        }catch (NameNotFoundException e) { ; }

        if (null == pi)
            return; // never happen

        CharSequence title = getResources().getText(R.string.about_app);
        StringBuilder strbldr = new StringBuilder();
        strbldr.append(getResources().getText(R.string.version)).append(" : ").append(pi.versionName).append("\n")
               .append(getResources().getText(R.string.about_app_email)).append("\n")
               .append(getResources().getText(R.string.about_app_blog)).append("\n")
               .append(getResources().getText(R.string.about_app_page)).append("\n");
        AlertDialog diag = mLnf.createAlertDialog(this, 0, title, strbldr.toString());
        diag.show();
    }

    private void
    onResult_pickPredefinedChannel(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;

        final String url = data.getStringExtra("url");
        eAssert(Utils.isValidValue(url));
        final String iconurl = data.getStringExtra("iconurl");
        // NOTE
        // Without using 'post', user may feel bad ui response.
        Utils.getUiHandler().post(new Runnable() {
            @Override
            public void
            run() {
                addChannel(url, iconurl);
            }
        });
    }

    private void
    setupToolButtons() {
        findViewById(R.id.btn_items_favorite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsFavorite(v);
            }
        });

        findViewById(R.id.btn_items_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsCategory(v);
            }
        });

        findViewById(R.id.btn_items_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsAll(v);
            }
        });

        findViewById(R.id.btn_add_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_addChannel(v);
            }
        });

        findViewById(R.id.btn_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_category(v);
            }
        });

        findViewById(R.id.btn_management).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_management(v);
            }
        });

        findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_setting(v);
            }
        });

        findViewById(R.id.btn_information).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_information(v);
                /* For scheduled update test.
                Cursor c = mDbp.queryChannel(DB.ColumnChannel.ID);
                c.moveToFirst();
                Calendar calNow = Calendar.getInstance();
                long dayms = calNow.getTimeInMillis() - Utils.dayBaseMs(calNow);
                dayms += 5000; // after 5 sec
                mDbp.updateChannel_schedUpdate(c.getLong(0), new long[] { dayms/1000 });
                c.close();
                ScheduledUpdateService.scheduleNextUpdate(Calendar.getInstance());
                */
            }
        });
    }

    public boolean
    isContextMenuOwner(ChannelListFragment fragment) {
        return fragment == mContextMenuOwner;
    }

    public void
    categoryDataSetChanged(long catId) {
        ChannelListFragment fragmentTo = getPagerAdapter().getFragment(catId);
        if (null != fragmentTo)
            fragmentTo.refreshListAsync();
    }

    @Override
    public void
    onTabSelected(Tab tab, FragmentTransaction ft) {
        onTabReselected(tab, ft);
    }

    @Override
    public void
    onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void
    onTabReselected(Tab tab, FragmentTransaction ft) {
        mPager.setCurrentItem(getPagerAdapter().getPosition(getTag(tab).categoryid));
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case REQC_PICK_PREDEFINED_CHANNEL:
            onResult_pickPredefinedChannel(resultCode, data);
            break;
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelListActivity ]";
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        mContextMenuOwner = getPagerAdapter().getPrimaryFragment();;
        mContextMenuOwner.onCreateContextMenu2(menu, v, menuInfo);
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        //logI("==> ChannelListActivity : onCreate");

        // TODO
        // Is this best place to put this line of code (sendReportMail())???
        // More consideration is required.

        // Send error report if exists.
        mUr.sendErrReportMail(this);
        // Send usage report if exists and time is passed enough.
        mUr.sendUsageReportMail(this);


        setContentView(R.layout.channel_list);

        setupToolButtons();

        // Setup Tabs
        mAb = getActionBar();
        mAb.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mAb.setDisplayShowTitleEnabled(false);
        mAb.setDisplayShowHomeEnabled(false);


        Feed.Category[] cats;
        cats = mDbp.getCategories();
        eAssert(cats.length > 0);

        for (Feed.Category cat : cats)
            addCategory(cat);

        mPager = (ViewPager)findViewById(R.id.pager);
        ChannelListPagerAdapter adapter = new ChannelListPagerAdapter(getSupportFragmentManager(),
                                                                      cats);
        mPager.setAdapter(adapter);

        selectDefaultAsSelected();

        mPager.setOnPageChangeListener(mPCListener);
        mDbWatcher = new DBWatcher();
    }


    @Override
    protected void
    onStart() {
        super.onStart();
    }

    @Override
    protected void
    onResume() {
        super.onResume();

        mDbWatcher.unregister();
        boolean needReload = mDbWatcher.isCategoryTableUpdated();
        mDbWatcher.reset();
        if (needReload) {
            // category table is changed outside of this activity.
            // restarting is required!!
            restartThisActivity();
            return;
        }

        if (null == mAb) {
            if (DBG) P.w("mAb(action bar) is NULL");
            mAb = getActionBar();
        }

    }

   @Override
    public void
    onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    protected void
    onPause() {
        mDbWatcher.register();
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
        mDbWatcher.unregister();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
