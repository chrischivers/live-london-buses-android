package io.chiv.livelondonbuses.map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import io.chiv.livelondonbuses.R;

public class RouteSelector extends DialogFragment {

    private static int selectCounter = 0;
    private final static int MAX_SELECTION = 10;

    static RouteSelector newInstance(ArrayList<String> allRoutesList, List<String> routesPreSelected) {
        RouteSelector rs = new RouteSelector();
        Bundle args = new Bundle();
        args.putStringArrayList("allRoutes", allRoutesList);
        ArrayList<String> preSelectedArrList = new ArrayList<>();
        preSelectedArrList.addAll(routesPreSelected);
        args.putStringArrayList("preSelected", preSelectedArrList);
        rs.setArguments(args);
        return rs;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        selectCounter = 0;
        super.onCreateDialog(savedInstanceState);
        ArrayList<String> allRoutes = getArguments().getStringArrayList("allRoutes");
        ArrayList<String> preSelectedRoutes = getArguments().getStringArrayList("preSelected");
        final CharSequence[] allRoutesCharSequence = allRoutes.toArray(new CharSequence[allRoutes.size()]);
        final boolean[] startingChecked = new boolean[allRoutesCharSequence.length];

        final ArrayList<Integer> selectedRoutesIndexes = new ArrayList<>();  // Where we track the selected items

        for (int i = 0; i < allRoutes.size(); i++) {
            if (preSelectedRoutes.contains(allRoutes.get(i))) {
                startingChecked[i] = true;
                selectedRoutesIndexes.add(i);
            } else {
                startingChecked[i] = false;
            }
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getResources().getString(R.string.selectRoutes) + " (maximum: " + MAX_SELECTION + ")")
                .setMultiChoiceItems(allRoutesCharSequence, startingChecked,
                        (dialog, which, isChecked) -> {
                            if (isChecked) {
                                if (selectCounter < MAX_SELECTION) {
                                    selectedRoutesIndexes.add(which);
                                    selectCounter++;
                                } else {
                                    Toast.makeText(getActivity().getApplicationContext(),
                                            "Maximum " + MAX_SELECTION + " routes permitted",
                                            Toast.LENGTH_LONG).show();
                                    ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                                }
                            } else if (selectedRoutesIndexes.contains(which)) {
                                selectCounter--;
                                selectedRoutesIndexes.remove(Integer.valueOf(which));
                            }
                        })
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    MapCallbacks activity = (MapCallbacks) getActivity();

                    ArrayList<String> selectedRoutes = new ArrayList<>();
                    for (int i = 0; i < selectedRoutesIndexes.size(); i++) {
                        Integer selectedRouteIndex = selectedRoutesIndexes.get(i);
                        selectedRoutes.add(allRoutesCharSequence[selectedRouteIndex].toString());
                    }
                    activity.onRouteSelectionReturn(selectedRoutes);
                })

                .setNeutralButton(R.string.clear, (dialogInterface, i) -> {
                    MapCallbacks activity = (MapCallbacks) getActivity();
                    selectedRoutesIndexes.clear();
                    selectCounter = 0;
                    activity.onRouteSelectionReturn(new ArrayList<>());
                })

                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                    selectedRoutesIndexes.clear();
                    selectCounter = 0;
                });

        return builder.create();
    }

}
