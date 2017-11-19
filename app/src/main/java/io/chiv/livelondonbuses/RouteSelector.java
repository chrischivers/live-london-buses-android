package io.chiv.livelondonbuses;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class RouteSelector extends DialogFragment {

    static RouteSelector newInstance(ArrayList<String> allRoutesList) {
        RouteSelector rs = new RouteSelector();
        Bundle args = new Bundle();
        args.putStringArrayList("allRoutes", allRoutesList);
        rs.setArguments(args);
        return rs;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        List<String> allRoutesList = getArguments().getStringArrayList("allRoutes");
        final CharSequence[] allRoutesCharSequenceArray = allRoutesList.toArray(new CharSequence[allRoutesList.size()]);
        final ArrayList<Integer> selectedRoutesIndexes = new ArrayList<>();  // Where we track the selected items
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.selectRoutes)
                .setMultiChoiceItems(allRoutesCharSequenceArray, null,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                if (isChecked) {
                                    selectedRoutesIndexes.add(which);
                                } else if (selectedRoutesIndexes.contains(which)) {
                                    selectedRoutesIndexes.remove(Integer.valueOf(which));
                                }
                            }
                        })
                // Set the action buttons
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        MapCallbacks activity = (MapCallbacks) getActivity();

                        ArrayList<String> selectedRoutes = new ArrayList<>();
                        for(int i = 0; i < selectedRoutesIndexes.size(); i ++) {
                            Integer selectedRouteIndex = selectedRoutesIndexes.get(i);
                            selectedRoutes.add(allRoutesCharSequenceArray[selectedRouteIndex].toString());
                        }
                        activity.onRouteSelectionReturn(selectedRoutes);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        //Something here
                    }
                });

        return builder.create();
    }
}
