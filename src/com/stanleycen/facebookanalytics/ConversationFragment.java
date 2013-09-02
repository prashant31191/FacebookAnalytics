package com.stanleycen.facebookanalytics;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.haarman.listviewanimations.swinginadapters.prepared.SwingBottomInAnimationAdapter;
import com.stanleycen.facebookanalytics.graph.Bar;
import com.stanleycen.facebookanalytics.graph.Line;
import com.stanleycen.facebookanalytics.graph.LineGraph;
import com.stanleycen.facebookanalytics.graph.LinePoint;
import com.stanleycen.facebookanalytics.graph.PieSlice;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by scen on 8/30/13.
 */
public class ConversationFragment extends Fragment {
    public FBThread fbThread;
    CardAdapter ca;

    public enum CardItems {
        TOTAL,
        PIE_MSG,
        PIE_CHAR,
        PIE_SENTFROM,
        LOADER,
        BAR_DOW,
        BAR_CPM,
        LINE_DAY,
        LINE_NIGHT
    };

    public static Fragment newInstance(Context context, FBThread fbThread) {
        ConversationFragment f = new ConversationFragment();
        f.fbThread = fbThread;
        return f;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setTitle(fbThread.title);
        getActivity().getActionBar().setSubtitle("" + DateUtils.getRelativeTimeSpanString(fbThread.lastUpdate.getMillis(),
                DateTime.now().getMillis(), DateUtils.MINUTE_IN_MILLIS, 0));
        ((MainActivity)getActivity()).unselectAllFromNav();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceBundle) {
        FBData fbData = GlobalApp.get().fb.fbData;
        if (fbData == null || fbData.lastUpdate == null) {
            return (ViewGroup)inflater.inflate(R.layout.fragment_error_data, null);
        }


        ViewGroup root = (ViewGroup)inflater.inflate(R.layout.fragment_conversation, null);

        ListView list = (ListView)root.findViewById(R.id.listView);
        Util.addSeparatingHeaderView(getActivity(), inflater, list);


        final List<CardItem> items = new ArrayList<CardItem>();

        items.add(new CardLoader(CardItems.LOADER.ordinal(), "Loading conversation"));

        ca = new CardAdapter(getActivity(), items, CardItems.values().length);
        SwingBottomInAnimationAdapter swingBottomInAnimationAdapter = new SwingBottomInAnimationAdapter(ca);
        swingBottomInAnimationAdapter.setAbsListView(list);
        list.setAdapter(swingBottomInAnimationAdapter);

        setRetainInstance(true);

        new Worker().execute();

        return root;
    }

    private class Worker extends AsyncTask<Void, Void, List<CardItem>> {
        @Override
        protected List<CardItem> doInBackground(Void... params) {
            Intent i = new Intent("com.stanleycen.facebookanalytics.update");
            i.putExtra("action", "indeterminate");
            getActivity().sendBroadcast(i);

            List<CardItem> ret = new ArrayList<CardItem>();

            Map<FBUser, MutableInt> charCount = new HashMap<FBUser, MutableInt>();
            Map<FBUser, MutableInt> msgCount = new HashMap<FBUser, MutableInt>();
            Map<FBUser, int[]> userMsgsPerHour = new HashMap<FBUser, int[]>();

            int[] messagesPerDow = new int[8];
            int[] messagesPerHour = new int[24];

            int mobileCount = 0;
            int webCount = 0;
            int otherCount = 0;

            for (FBMessage fbMessage : fbThread.messages) {
                MutableInt cc = charCount.get(fbMessage.from);
                if (cc == null) charCount.put(fbMessage.from, new MutableInt(fbMessage.body.length()));
                else cc.add(fbMessage.body.length());

                MutableInt mc = msgCount.get(fbMessage.from);
                if (mc == null) msgCount.put(fbMessage.from, new MutableInt());
                else mc.increment();
                messagesPerDow[fbMessage.timestamp.getDayOfWeek()]++;
                messagesPerHour[fbMessage.timestamp.getHourOfDay()]++;
                int hour = fbMessage.timestamp.getHourOfDay();
                int[] userMPH = userMsgsPerHour.get(fbMessage.from);
                if (userMPH == null) {
                    userMsgsPerHour.put(fbMessage.from, new int[24]);
                }
                userMsgsPerHour.get(fbMessage.from)[hour]++;

                switch (fbMessage.source) {
                    case MOBILE:
                        mobileCount++;
                        break;
                    case WEB:
                    case OTHER:
                        webCount++;
                        break;
                    default:
                }
            }

            ret.add(new CardTotal(CardItems.TOTAL.ordinal(), fbThread));

            CardPieChart msgCard = new CardPieChart(CardItems.PIE_MSG.ordinal(), "Message distribution");
            CardPieChart charCard = new CardPieChart(CardItems.PIE_CHAR.ordinal(), "Character distribution");
            CardBarChart charsPerMessage = new CardBarChart(CardItems.BAR_CPM.ordinal(), "Characters per message");
            ArrayList<Bar> cpmBars = new ArrayList<Bar>();
            ArrayList<PieSlice> msgSlices = new ArrayList<PieSlice>(), charSlices = new ArrayList<PieSlice>();

            int idx = 0;

            msgCard.setSlices(msgSlices);
            charCard.setSlices(charSlices);
            ret.add(msgCard);
            ret.add(charCard);

            for (FBUser person : fbThread.participants) {
                String name = person == GlobalApp.get().fb.fbData.me ? "You" : person.name;
                name = name.split(" ")[0];
                Bar b = new Bar();
                b.setColor(Util.colors[idx % Util.colors.length]);
                if (msgCount.get(person) != null) b.setValue(msgCount.get(person).get() == 0 ? 0 : (float)charCount.get(person).get() / (float)msgCount.get(person).get());
                else b.setValue(0);
                b.setName(name);
                cpmBars.add(b);

                PieSlice slice = new PieSlice();
                slice.setColor(Util.colors[idx % Util.colors.length]);
                slice.setTitle(name);
                if (msgCount.get(person) != null) slice.setValue(msgCount.get(person).get());
                else slice.setValue(0);
                msgSlices.add(slice);

                slice = new PieSlice();
                slice.setColor(Util.colors[idx % Util.colors.length]);
                slice.setTitle(name);
                if (charCount.get(person) != null) slice.setValue(charCount.get(person).get());
                else slice.setValue(0);
                charSlices.add(slice);
                ++idx;
            }
            charsPerMessage.setBars(cpmBars);
            ret.add(charsPerMessage);

            CardBarChart mostActiveDow = new CardBarChart(CardItems.BAR_DOW.ordinal(), "Most active day of week");
            int firstDow = Util.getJodaFirstDayOfWeek();
            ArrayList<Bar> dowBars = new ArrayList<Bar>();
            final DateTime tmp = new DateTime();
            for (int offset = 0; offset < 7; offset++) {
                Bar b = new Bar();
                b.setName(tmp.withDayOfWeek((firstDow - 1 + offset) % 7 + 1).dayOfWeek().getAsShortText());
                b.setColor(Util.colors[offset % Util.colors.length]);
                b.setValue(messagesPerDow[(firstDow - 1 + offset) % 7 + 1]);
                dowBars.add(b);
            }
            mostActiveDow.setBars(dowBars);
            ret.add(mostActiveDow);

            CardLineChart daytimeActivity = new CardLineChart(CardItems.LINE_DAY.ordinal(), "Daytime activity");
            CardLineChart nighttimeActivity = new CardLineChart(CardItems.LINE_NIGHT.ordinal(), "Nighttime activity");

            final DateTimeFormatter hourFormatter = DateTimeFormat.forPattern("h a");


            Map<FBUser, Line> userDaytimeLines = new HashMap<FBUser, Line>();
            Map<FBUser, Line> userNighttimeLines = new HashMap<FBUser, Line>();
            idx = 0;
            for (FBUser user : fbThread.participants) {
                String name = user == GlobalApp.get().fb.fbData.me ? "You" : user.name;
                name = name.split(" ")[0];
                if (msgCount.get(user) != null) {
                    Line line = new Line();
                    line.setName(name);
                    line.setShowingPoints(true);
                    line.setColor(Util.colors[idx % Util.colors.length]);
                    userDaytimeLines.put(user, line);
                    Line line2 = new Line();
                    line2.setName(name);
                    line2.setShowingPoints(true);
                    line2.setColor(Util.colors[idx % Util.colors.length]);
                    userNighttimeLines.put(user, line2);
                    idx++;
                }
            }
            Line daytimeLine = new Line();
            daytimeLine.setName("Total");
            daytimeLine.setShowingPoints(true);
            daytimeLine.setColor(Util.colors[idx]);

            Line nighttimeLine = new Line();
            nighttimeLine.setName("Total");
            nighttimeLine.setShowingPoints(true);
            nighttimeLine.setColor(Util.colors[idx]);

            int daytimemx = 0;
            int nighttimemx = 0;
            for (int h = 6; h <= 17; h++) {
                //6am to 5pm
                daytimeLine.addPoint(new LinePoint(h, messagesPerHour[h]));
                for (Map.Entry<FBUser, Line> entry : userDaytimeLines.entrySet()) {
                    int[] da = userMsgsPerHour.get(entry.getKey());
                    entry.getValue().addPoint(new LinePoint(h, da == null ? 0 : da[h]));
                }
                daytimemx = Math.max(daytimemx, messagesPerHour[h]);
            }
            int iidx = 0;
            for (int h = 18; h <= 23; h++) {
                nighttimeLine.addPoint(new LinePoint(iidx, messagesPerHour[h]));
                for (Map.Entry<FBUser, Line> entry : userNighttimeLines.entrySet()) {
                    int[] da = userMsgsPerHour.get(entry.getKey());
                    entry.getValue().addPoint(new LinePoint(iidx, da == null ? 0 : da[h]));
                }
                iidx++;
                nighttimemx = Math.max(nighttimemx, messagesPerHour[h]);
            }
            for (int h = 0; h <= 5; h++) {
                nighttimeLine.addPoint(new LinePoint(iidx, messagesPerHour[h]));
                for (Map.Entry<FBUser, Line> entry : userNighttimeLines.entrySet()) {
                    int[] da = userMsgsPerHour.get(entry.getKey());
                    entry.getValue().addPoint(new LinePoint(iidx, da == null ? 0 : da[h]));
                }
                iidx++;
                nighttimemx = Math.max(nighttimemx, messagesPerHour[h]);
            }
            daytimeActivity.setxFormatter(new LineGraph.LabelFormatter() {
                @Override
                public String format(int idx, int tot, float min, float max, int ptsPerDelta) {
                    return hourFormatter.print(tmp.withHourOfDay((idx*ptsPerDelta) + 6));
                }
            });

            daytimeActivity.setyFormatter(new LineGraph.LabelFormatter() {
                @Override
                public String format(int idx, int tot, float min, float max, int ptsPerDelta) {
                    return (int)((max - min)*((float)idx/(float)(tot - 1))+min) + (idx==tot-1?" messages" : "");
                }
            });

            nighttimeActivity.setxFormatter(new LineGraph.LabelFormatter() {
                @Override
                public String format(int idx, int tot, float min, float max, int ptsPerDelta) {
                    return hourFormatter.print(tmp.withHourOfDay((idx*ptsPerDelta) + 6).plusHours(12));
                }
            });

            nighttimeActivity.setyFormatter(new LineGraph.LabelFormatter() {
                @Override
                public String format(int idx, int tot, float min, float max, int ptsPerDelta) {
                    return (int)((max - min)*((float)idx/(float)(tot - 1))+min) + (idx==tot-1?" messages" : "");
                }
            });

            ArrayList<Line> daytimeLines = new ArrayList<Line>(), nighttimeLines = new ArrayList<Line>();
            for (Map.Entry<FBUser, Line> entry : userDaytimeLines.entrySet()) {
                daytimeLines.add(entry.getValue());
            }
            for (Map.Entry<FBUser, Line> entry : userNighttimeLines.entrySet()) {
                nighttimeLines.add(entry.getValue());
            }
            daytimeLines.add(daytimeLine);
            nighttimeLines.add(nighttimeLine);
            daytimeActivity.setLines(daytimeLines);
            nighttimeActivity.setLines(nighttimeLines);
            daytimeActivity.setRangeY(0, Util.roundUpNiceDiv4(daytimemx));
            nighttimeActivity.setRangeY(0, Util.roundUpNiceDiv4(nighttimemx));


            ret.add(daytimeActivity);
            ret.add(nighttimeActivity);


            CardPieChart sentFromCard = new CardPieChart(CardItems.PIE_SENTFROM.ordinal(), "Devices sent from");
            PieSlice webSlice = new PieSlice();
            webSlice.setColor(Util.colors[0]);
            webSlice.setTitle("Web");
            webSlice.setValue(webCount);
            PieSlice mobileSlice = new PieSlice();
            mobileSlice.setColor(Util.colors[1]);
            mobileSlice.setTitle("Mobile");
            mobileSlice.setValue(mobileCount);
//            PieSlice otherSlice = new PieSlice();
//            otherSlice.setColor(Util.colors[2]);
//            otherSlice.setTitle("Other");
//            otherSlice.setValue(otherCount);
            sentFromCard.setSlices(new ArrayList<PieSlice>(Arrays.asList(webSlice, mobileSlice)));
            ret.add(sentFromCard);

            return ret;
        }

        @Override
        protected void onPostExecute(List<CardItem> result) {
            ca.clear();
            ca.addAll(result);
        }
    }
}
