package com.ubc.capstonegroup70;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.Vector;
import java.util.Collections;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.String;

import com.bitalino.util.SensorDataConverter;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
//import com.example.bluetoothnew.R;
import ceu.marten.bitadroid.R;
import ceu.marten.model.Constants;
import ceu.marten.model.io.DataManager;
import ceu.marten.ui.NewRecordingActivity;

import com.jjoe64.graphview.CustomLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.VideoView;
import ceu.marten.bitadroid.R;

import ceu.marten.ui.Graph;

public class ReplayStoredGraphActivity extends Activity {
    // Tag for logging errors
    private static final String DSGA_TAG = ReplayStoredGraphActivity.class.getName();
    // Progress Dialog Object
    private ProgressDialog prgDialog;

    private Vector<Double> dataSetRAW = new Vector<Double>();

    public static final File externalStorageDirectory = Environment.getExternalStorageDirectory();
    public String recordingName = "EMG_DATA";
    public String recordingTimePattern = ".{168}(\\d+).(\\d+).(\\d\\d)(\\d+).(\\d+).(\\d\\d)";
    private String graphTitle = new String();
    private static final String TAG = NewRecordingActivity.class.getName();
    private GraphViewSeries rawSeries;

    int max = 0;
    int min = 0;

    // Data for determining the appropriate scale for the x-axis
    private double samplingFrequency = 1000;

    private String recordingDate;
    private String recordingTime;

    private String durationTimePattern = "(duration)(\\d+)";
    private long duration;

    // Video stuff
    private LinearLayout videoLayout;
    private boolean readingText = false;
    private CustomVideoView myCustomVideoView;
    private int position = 0;
    private ProgressDialog progressDialog;
    private MediaController mediaControls;

    private Graph graph;
    private final Handler mHandler = new Handler();
    private Runnable graphTimer;

    private long delay;
    private int graph2LastXValue = 0;

    private boolean graphStarted = false;
    private double secondsInterval;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Initiating creation of ReplayStoredGraphActivity class");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            recordingName = extras.getString("FILE_NAME");
            graphTitle = extras.getString("PATIENT_NAME");
        } else
            System.out.println("Unable to retrieve FILE_NAME");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay_stored_graph);
        prgDialog = new ProgressDialog(this);

        //replayButton = (Button) findViewById(R.id.nr_bttn_StartPause);

        //initialize the VideoView
        myCustomVideoView = (CustomVideoView) findViewById(R.id.videoView);
        myCustomVideoView.setPlayPauseListener(new CustomVideoView.PlayPauseListener() {
            @Override
            public void onPlay() {
                System.out.println("listen - Play!");
                if(!graphStarted)
                    playAll();
                else
                    mHandler.post(graphTimer);
            }

            @Override
            public void onPause() {
                System.out.println("listen - Pause!");
                mHandler.removeCallbacks(graphTimer);
            }
        });


        //set the media controller buttons
        if (mediaControls == null) {
            mediaControls = new MediaController(ReplayStoredGraphActivity.this);

//            mediaControls = (MediaController) findViewById(R.id.mediaController);
//            mediaControls.show();
        }


        try {
            File folder = new File(externalStorageDirectory + Constants.APP_DIRECTORY, recordingName);
            File[] files = folder.listFiles();
            for(File file : files) {
                if(file.getName().endsWith(".mp4")) {
                    //set the media controller in the VideoView
                    myCustomVideoView.setMediaController(mediaControls);

                    //set the uri of the video to be played
                    myCustomVideoView.setVideoURI(Uri.parse(externalStorageDirectory + Constants.APP_DIRECTORY + recordingName + "/" + file.getName()));
                }
            }

        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }
        myCustomVideoView.requestFocus();
        //we also set an setOnPreparedListener in order to know when the video file is ready for playback
        myCustomVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            public void onPrepared(MediaPlayer mediaPlayer) {
                // close the progress bar and play the video
                // progressDialog.dismiss();
                // if we have a position on savedInstanceState, the video playback should start from here
                myCustomVideoView.seekTo(position);
                System.out.println("seek to : " + position + " onPrepared()");
//                if(playing)
//                    playAll(position);
            }
        });

        // Hide the video layout
        videoLayout = (LinearLayout) findViewById(R.id.videoLayoutId);
        videoLayout.setVisibility(View.VISIBLE);

        // store all data points
        rawSeries = new GraphViewSeries(new GraphViewData[]{});
        // create graph
//        graph = new Graph(this, "title1");

        // Load the text
        new ReadFileService().execute();

//        graphData(dataSetRAW, 100);
    }

    /**
     * Calculates the range (min and max) of values of the dataSet vector
     * Parameters:	none
     * Outputs:	Double[2]; Double[0] = min, Double[1] = max
     */
    private double[] calculateRange(Vector<Double> dataSet) {
        double dataRange[] = {0, 0}; //{y-min, y-max}
        Object dataMin = Collections.min(dataSet);
        Object dataMax = Collections.max(dataSet);
        dataRange[0] = (double) dataMin;
        dataRange[1] = (double) dataMax;
        return dataRange;
    }

    private void graphData(final Vector<Double> dataSet, int viewPort) {
        System.out.println("Defining data set.");

        // Determine the appropriate graphSeries to add depending on dataSet that was passed
        GraphViewSeries graphSeries;
        TextView yAxisString = (TextView) findViewById(R.id.graph_yAxis);
        TextView xAxisString = (TextView) findViewById(R.id.graph_xAxis);
            graphSeries = rawSeries;
            yAxisString.setText("Voltage\n(mV)");
            xAxisString.setText("Time (Hour:Minute:Second)");

        // Format graph labels to show the appropriate domain on x-axis
        GraphView graphView = new LineGraphView(this, graphTitle) {
            protected String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    long xValue;
                    if (value < 0.000) {
                        return "00:00:00";
                    }
                    xValue = (long) value;
                    // Set the x-axis to use the time domain
                    samplingFrequency = 1/secondsInterval * 1000;
                    return String.format("%02d:%02d:%02d", (int) ((xValue / (samplingFrequency * 60 * 60)) % 24), (int) ((xValue / (samplingFrequency * 60)) % 60), (int) ((xValue / samplingFrequency)) % 60);
                } else {
                        return String.format("%.2f", (double) value);
                }
            }
        };

        int yInterval = calculateYScale(dataSet);
        int yLabel = max;
        while ((yLabel - min) % yInterval != 0) {
            yLabel++;
        }

        // Calculate appropriate interval value in x-direction
        int xInterval = calculateXScale(dataSet);
        int xLabel = dataSet.size();
        while (xLabel % xInterval != 0) {
            xLabel++;
        }

//        graphView.addSeries(graphSeries);
        graphView.addSeries(rawSeries);
        ((LineGraphView) graphView).setDrawBackground(false);

        // Settings for the graph to be scrollable and scalable
        graphView.setScalable(true);
//        graphView.setScrollable(true);
        // Settings for graph view port size
        if (dataSet.size() < viewPort)
            graphView.setViewPort(0, dataSet.size());
        else
            graphView.setViewPort(0, viewPort);
//	  graphView
        // Settings for the graph styling
        graphView.setManualYAxisBounds(yLabel, min);
        graphView.getGraphViewStyle().setGridColor(Color.BLACK);
        graphView.getGraphViewStyle().setHorizontalLabelsColor(Color.BLACK);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.BLACK);
        graphView.getGraphViewStyle().setVerticalLabelsWidth(80);

        LinearLayout layout = (LinearLayout) findViewById(R.id.dataGraph);
        layout.removeAllViews();
        layout.addView(graphView);
    }

    private int calculateYScale(Vector<Double> dataSet) {
        // Calculate range of y values
        //double yBounds[] = {0, 0};
        double yBounds[] = calculateRange(dataSet);
        // Calculate the appropriate max value and min values in y-direction
        max = (int) yBounds[1] + 1;
        if (yBounds[0] >= 0) {
            min = (int) yBounds[0];
        } else {
            min = (int) yBounds[0] - 1;
        }
        // Calculate interval level of labels in y-direction
        int yInterval;
//		if ((max-min) <= 5) {
//			yInterval = 0.5;
//		}
//		else
        if ((max - min) <= 10) {
            yInterval = 1;
        } else if ((max - min) <= 50) {
            yInterval = 2;
        } else if ((max - min) <= 100) {
            yInterval = 5;
        } else if ((max - min) <= 200) {
            yInterval = 10;
        } else {
            yInterval = 25;
        }
        return yInterval;
    }

    private int calculateXScale(Vector<Double> dataSet) {
        int xInterval;
        int xMax = dataSet.size();
        if (xMax <= 10) {
            xInterval = 1;
        } else if (xMax <= 50) {
            xInterval = 5;
        } else if (xMax <= 100) {
            xInterval = 10;
        } else {
            xInterval = 20;
        }
        return xInterval;
    }

    /**
     * Calculates the mean value (average) of a given dataSet vector and subtracts the mean from the original dataset
     * Parameters:		Vector<Double> - Vector of type double containing the values from which the mean will be calculated
     * Outputs:			Vector<Double> - Value of the original vector with the mean value subtracted
     */
    private Vector<Double> removeMean(Vector<Double> dataSet) {
        double mean = 0;
        for (int i = 0; i < dataSet.size(); i++) {
            mean += dataSet.elementAt(i);
        }
        mean = mean / dataSet.size();

        for (int i = 0; i < dataSet.size(); i++) {
            dataSet.set(i, dataSet.elementAt(i) - mean);
        }
        return dataSet;
    }

    /**
     * Play media and graph at the same time
     * @author Angelo Chung 2015
     *
     */
    private void playAll()   {
        graphStarted = true;

        System.out.println("delay : " + delay);
        graphTimer = new Runnable() {
            double offset = 0;
            @Override
            public void run() {
//                while(graph2LastXValue * secondsInterval <= myCustomVideoView.getDuration()){
                    rawSeries.appendData(new GraphViewData((graph2LastXValue * secondsInterval), dataSetRAW.get((int) (graph2LastXValue * secondsInterval))), true, dataSetRAW.size());
                    offset = offset + secondsInterval - Math.floor(secondsInterval);
                    if(offset >= 1) {
                        System.out.println("offset : " + offset);
                        mHandler.postDelayed(this, 100*(int)(secondsInterval+offset));
                        offset = offset - Math.floor(offset);
                    } else {
                        mHandler.postDelayed(this, 100*(int)secondsInterval);
                    }
                    graph2LastXValue += 100;
//                }

//            System.out.println("x : " + graph2LastXValue*delay + " y : " + dataSetRAW.get(graph2LastXValue));
                //mHandler.postDelayed(this, (long)Math.ceil(secondsInterval*15));
//                for(int index = graph2LastXValue; index < 20 || index < dataSetRAW.size(); index++){
//                    rawSeries.appendData(new GraphViewData(index * secondsInterval, dataSetRAW.get(graph2LastXValue)), true, dataSetRAW.size());
//                }
//                graph2LastXValue += 20;
//                mHandler.postDelayed(this, (long)Math.ceil(secondsInterval*20));
            }
        };
        mHandler.postDelayed(graphTimer, 0);
    }

    /**
     * Destroys activity
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    /**
     * When user presses on the UP button in Action Bar, simulate a back button press to return to parent activity
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Asynchronous Task for reading the data points within the data file
     *
     * @author Caleb Ng (2015)
     *         //<Params, Progress, Result>
     */
    class ReadFileService extends AsyncTask<Void, String, Boolean> {

        @Override
        protected Boolean doInBackground(Void... args) {
            //Scanner strings = null;
            // Triggers only for text files
            try {
                System.out.println(externalStorageDirectory + Constants.APP_DIRECTORY + recordingName + Constants.ZIP_FILE_EXTENTION);
                File folder = new File(externalStorageDirectory + Constants.APP_DIRECTORY, recordingName);
                File[] files = folder.listFiles();
                for(File file : files) {
                    if(file.getName().endsWith(".txt")) {
                        readingText = true;
                        Scanner strings = new Scanner(file);

                        // Determine the start date and time from the header text
                        System.out.println("Extracting recording start date and time.");
                        Pattern pattern = Pattern.compile(".{184}");
                        String extracted = strings.findInLine(pattern);
                        pattern = Pattern.compile(recordingTimePattern);
                        Matcher matcher = pattern.matcher(extracted);
                        if (matcher.find()) {
                            if (matcher.groupCount() == 6) {
                                String Day = matcher.group(1);
                                String Month = matcher.group(2);
                                String Year = matcher.group(3);
                                String Hour = matcher.group(4);
                                String Minute = matcher.group(5);
                                String Second = matcher.group(6);
                                recordingDate = Month + "/" + Day + "/20" + Year;
                                recordingTime = Hour + ":" + Minute + ":" + Second;
                                graphTitle += " on " + recordingDate + " at " + recordingTime;

                            } else
                                Log.e(DSGA_TAG, "ERROR: Insufficient number of matches found: " + matcher.groupCount());
                        }

                        // Determine the recording duration from the header text
                        System.out.println("Extracting recording duration.");
                        Pattern patternD = Pattern.compile(".{236}");
                        String extractedD = strings.findInLine(patternD);
                        patternD = Pattern.compile(durationTimePattern);
                        Matcher matcherD = patternD.matcher(extractedD);
                        if (matcherD.find()) {
                            if (matcherD.groupCount() == 2) {
                                String durationTitle = matcherD.group(1);
                                String durationTime = matcherD.group(2);
                                //System.out.println("duration : "+durationTitle +" " + durationTime);
                                duration = Long.parseLong(durationTime);
                            } else
                                Log.e(DSGA_TAG, "ERROR: Insufficient number of matches found: " + matcherD.groupCount());
                        }


                        // Use new line character as a delimiter for file data
                        strings.nextLine();
                        strings.useDelimiter("\n *");

                        // Loops for as long as there are more data points to be read from the text file
                        while (readingText && strings.hasNext()) {
                            double dataPoint = Double.parseDouble(strings.next());
//                            System.out.println("datapoint : " +dataPoint);
                            dataSetRAW.add(dataPoint);
                        }
                        System.out.println("Closing strings.");
                        strings.close();
                        return true;
                    } // if we're going to extract the .mp4 video file
                    else {
                        readingText = false;
                    }
                }
            } catch(Exception e){
                return false;
            }
            return true;
        }

        protected void onProgressUpdate(String... progress) {
            //called when the background task makes any progress
        }

        protected void onPreExecute() {
            //called before doInBackground() is started
            super.onPreExecute();
            // Show Progress Bar Dialog before calling doInBackground method
//			showDialog(progress_bar_type);
            prgDialog.setTitle("Opening File");
            prgDialog.setMessage("Opening " + recordingName + "\nPlease wait...");
            prgDialog.setCancelable(false);
            prgDialog.show();
            return;
        }

        protected void onPostExecute(Boolean readFileSuccess) {
            if (readingText) {
                // called after doInBackground() has finished
                // Check if the file was read successfully. If not, output error message and generate sample set of data
                if (!readFileSuccess) {

                    Random randomGenerator = new Random();
                    System.out.println("@IOERROR: Unable to read from file. Creating random dataset");
                    for (int i = 0; i < 100; i++) {
                        dataSetRAW.add(randomGenerator.nextDouble());
                    }
                }

                // Prepare data set for graphing
                dataSetRAW = removeMean(dataSetRAW);
//                rawSeries = new GraphViewSeries(new GraphViewData[]{});
                System.out.println("DSGA-TAG: Number of samples read is " + dataSetRAW.size());

                // printing the data to
//                for (int i = 0; i < dataSetRAW.size(); i++) {
//                    rawSeries.appendData(new GraphViewData(i, dataSetRAW.get(i)), true, dataSetRAW.size());
//                }
                //graphData(dataSetRAW, 100);

                prgDialog.dismiss();
                prgDialog = null;
                delay = myCustomVideoView.getDuration()/dataSetRAW.size();
                secondsInterval = myCustomVideoView.getDuration()*1.0/dataSetRAW.size();
                System.out.println("D_duration : " + duration);
                System.out.println("D_dataSize : " + dataSetRAW.size());
                System.out.println("D_delay : " + delay);
                System.out.println("D_secondsInterval : " + secondsInterval);
            }

            // done with reading/processing text
            readingText = false;
            graphData(dataSetRAW, 100);
            return;
        }
    }

//
//    /**
//     * @author Angelo Chung (2015)
//     * Hide or Display the video layout.
//     */
//    public void onClickedShowVideo(View view) {
//        CheckBox showVideoCheckBox = (CheckBox) findViewById(R.id.showVideoId);
//        if (showVideoCheckBox.isChecked()) {
//            videoLayout.setVisibility(View.VISIBLE);
//        } else {
//            videoLayout.setVisibility(View.GONE);
//        }
//    }
    public void onClickedReplay(View view){
        System.out.println("delay : " + delay);
        graphTimer = new Runnable() {
            @Override
            public void run() {
                graph2LastXValue += 1;
                rawSeries.appendData(new GraphViewData(graph2LastXValue * delay, dataSetRAW.get(graph2LastXValue)), true, dataSetRAW.size());
//                    System.out.println("x : " + graph2LastXValue*delay + " y : " + dataSetRAW.get(graph2LastXValue));
                    mHandler.postDelayed(this, delay);
//                }
            }
        };
        mHandler.postDelayed(graphTimer, 1000);

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        //we use onSaveInstanceState in order to store the video playback position for orientation change
        savedInstanceState.putInt("Position", myCustomVideoView.getCurrentPosition());
        myCustomVideoView.pause();
    }


    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //we use onRestoreInstanceState in order to play the video playback from the stored position
        position = savedInstanceState.getInt("Position");
        myCustomVideoView.seekTo(position);
        //put redrawing data here
        System.out.println("seek to : " + position + " onRestoreInstanceState()");
//        playAll(position);
    }

    /**
     * Play media and graph at the same time
     * @author Angelo Chung 2015
     */
    class GraphRunnable implements Runnable {
        private int position;
        public GraphRunnable(int position){
            this.position = position;
        }
        @Override
        public void run(){
            try{
                for(int i = position; i< dataSetRAW.size(); i++) {
                    rawSeries.appendData(new GraphViewData(i*secondsInterval, dataSetRAW.get(i)), true, dataSetRAW.size());
                    System.out.println("x : " + i*delay + " y : " + dataSetRAW.get(i));
                    Thread.sleep(delay);
                }
            }catch(InterruptedException exception){}
        }
    }
//    @Override
//    public void onResume(){
//        super.onResume();
//        playAll(0);
//    }
}
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_replay_stored_graph, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

