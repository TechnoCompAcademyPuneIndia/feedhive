package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RSS;

public class FeederActivity extends ListActivity {
    // Request codes.
    static protected final int RCAddChannel   = 0;
    static protected final int RCReadChannel  = 1;

    private Cursor
    adapterCursorQuery() {
        return DB.db().query(
                DB.TABLE_RSSCHANNEL,
                new DB.ColumnRssChannel[]
                        { DB.ColumnRssChannel.ID, // Mandatory.
                          DB.ColumnRssChannel.TITLE,
                          DB.ColumnRssChannel.DESCRIPTION,
                          DB.ColumnRssChannel.URL }
                );
    }

    private void
    refreshList() {
        // [ NOTE ]
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery();
        ((ChannelListAdapter)getListAdapter()).swapCursor(newCursor).close();
        ((ChannelListAdapter)getListAdapter()).notifyDataSetChanged();
    }


    private void
    onOpt_addChannel() {
        // Create "Enter Url" dialog
        LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.enter_url_dialog, (ViewGroup)findViewById(R.id.root));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.channel_url);
        // Set action for dialog.
        EditText edit = (EditText)layout.findViewById(R.id.url);
        edit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction())
                    && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    // Perform action on key press
                    //Toast.makeText(this, "hahah", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    addChannel(((EditText)v).getText().toString());
                    return true;
                }
                return false;
            }
        });

        dialog.show();
    }

    private void
    addChannel(String url) {
        eAssert(url != null);

        RSS.Channel ch = new RSS.Channel();
        ch.url = url;

        // Just add url. at this times.
        new DBPolicy().insertRSSChannel(ch);

        refreshList();
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //setListAdapter(new ChannelListCursorAdapter(this, R.layout.channel_list_row, adapterCursorQuery()));
    }

    public void
    onAllButtonClicked(View v) {
        // id '0' means 'all'
        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", 0);
        startActivityForResult(intent, RCReadChannel);
    }

    @Override
    protected void
    onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(this, ItemReaderActivity.class);
        intent.putExtra("channelid", id);
        startActivityForResult(intent, RCReadChannel);
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainopt, menu);
		return true;
	}

	@Override
	public boolean
	onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case R.id.add_channel:
		    onOpt_addChannel();
		    break;
		}
		return true;
	}

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void
    onDestroy() {
        ((ChannelListAdapter)getListAdapter()).getCursor().close();
        super.onDestroy();
    }
}