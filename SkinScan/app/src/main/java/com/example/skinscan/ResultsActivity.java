package com.example.skinscan;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ResultsActivity extends AppCompatActivity {

    private ProgressBar ack_per, bcc_per, mel_per, nev_per, scc_per, sek_per;
    private TextView ack_txt, bcc_txt, mel_txt, nev_txt, scc_txt, sek_txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        ack_per = findViewById(R.id.ack_bar);
        bcc_per = findViewById(R.id.bcc_bar);
        mel_per = findViewById(R.id.mel_bar);
        nev_per = findViewById(R.id.nev_bar);
        scc_per = findViewById(R.id.scc_bar);
        sek_per = findViewById(R.id.sek_bar);

        ack_txt = findViewById(R.id.ack_txt);
        bcc_txt = findViewById(R.id.bcc_txt);
        mel_txt = findViewById(R.id.mel_txt);
        nev_txt = findViewById(R.id.nev_txt);
        scc_txt = findViewById(R.id.scc_txt);
        sek_txt = findViewById(R.id.sek_txt);

        float[] scores = getIntent().getFloatArrayExtra("outputs");
        Log.d("Results Activity", "Scores in results activity");
        for(int i = 0; i < 6; i++)
            Log.d("Results Activity", String.valueOf(scores[i]));
        
        setProgressBar(ack_per, scores[0]);
        setProgressBar(bcc_per, scores[1]);
        setProgressBar(mel_per, scores[2]);
        setProgressBar(nev_per, scores[3]);
        setProgressBar(scc_per, scores[4]);
        setProgressBar(sek_per, scores[5]);

        ack_txt.setText(String.format("%.2f", scores[0]));
        bcc_txt.setText(String.format("%.2f", scores[1]));
        mel_txt.setText(String.format("%.2f", scores[2]));
        nev_txt.setText(String.format("%.2f", scores[3]));
        scc_txt.setText(String.format("%.2f", scores[4]));
        sek_txt.setText(String.format("%.2f", scores[5]));

    }

    private void setProgressBar(ProgressBar current_bar, float current_score) {

        current_bar.setMax(100);
        current_bar.setProgress((int)(current_score * 100));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        this.finish();
    }

    public void ok_button_pressed(View view){
        finish();
    }
}