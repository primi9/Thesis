package com.example.skinscan;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.TextView;

public class waitResultsDialog {

    Context context;
    Dialog dialog;

    public waitResultsDialog(Context context){
        this.context = context;
    }

    public void showDialog(String msg){

        dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView txt_dialog = dialog.findViewById(R.id.pg_txt);
        txt_dialog.setText(msg);

        dialog.create();
        dialog.show();

    }

    public void endDialog(){
        dialog.dismiss();
    }
}
