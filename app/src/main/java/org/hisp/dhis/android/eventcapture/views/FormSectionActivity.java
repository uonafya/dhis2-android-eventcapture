package org.hisp.dhis.android.eventcapture.views;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.hisp.dhis.android.eventcapture.EventCaptureApp;
import org.hisp.dhis.android.eventcapture.FormComponent;
import org.hisp.dhis.android.eventcapture.R;
import org.hisp.dhis.android.eventcapture.presenters.FormSectionPresenter;
import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.ui.adapters.OnPickerItemClickListener;
import org.hisp.dhis.client.sdk.ui.fragments.DatePickerDialogFragment;
import org.hisp.dhis.client.sdk.ui.fragments.FilterableDialogFragment;
import org.hisp.dhis.client.sdk.ui.models.FormSection;
import org.hisp.dhis.client.sdk.ui.models.Picker;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import fr.castorflex.android.circularprogressbar.CircularProgressBar;

import static org.hisp.dhis.client.sdk.utils.Preconditions.isNull;
import static org.hisp.dhis.client.sdk.utils.StringUtils.isEmpty;

// TODO check if configuration changes are handled properly
public class FormSectionActivity extends AppCompatActivity implements FormSectionView {
    private static final String ARG_EVENT_UID = "arg:eventUid";
    private static final String ARG_IS_EVENT_NEW = "arg:isEventNew";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final int LOCATION_REQUEST_CODE = 42;
    private static final String TAG = FormSectionActivity.class.getSimpleName();

    @Inject
    FormSectionPresenter formSectionPresenter;

    // root layout
    CoordinatorLayout coordinatorLayout;

    // collapsing toolbar views
    TextView textViewReportDate;
    LinearLayout linearLayoutCoordinates;
    EditText editTextLatitude;
    EditText editTextLongitude;
    AppCompatImageView locationIcon;
    AppCompatImageView locationIconCancel;
    CircularProgressBar locationProgressBar;
    FrameLayout locationButtonLayout;

    // section tabs and view pager
    TabLayout tabLayout;
    ViewPager viewPager;
    FloatingActionButton fabComplete;

    FilterableDialogFragment sectionDialogFragment;
    AlertDialog alertDialog;

    public static void navigateToNewEvent(Activity activity, String eventUid) {
        navigateTo(activity, eventUid, true);
    }

    public static void navigateToExistingEvent(Activity activity, String eventUid) {
        navigateTo(activity, eventUid, false);
    }

    private static void navigateTo(Activity activity, String eventUid, boolean isEventNew) {
        isNull(activity, "activity must not be null");

        Intent intent = new Intent(activity, FormSectionActivity.class);
        intent.putExtra(ARG_EVENT_UID, eventUid);
        intent.putExtra(ARG_IS_EVENT_NEW, isEventNew);
        activity.startActivity(intent);
    }

    private String getEventUid() {
        if (getIntent().getExtras() == null || getIntent().getExtras()
                .getString(ARG_EVENT_UID, null) == null) {
            throw new IllegalArgumentException("You must pass event uid in intent extras");
        }

        return getIntent().getExtras().getString(ARG_EVENT_UID, null);
    }

    private boolean isEventNew() {
        return getIntent().getExtras().getBoolean(ARG_IS_EVENT_NEW, false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_sections);

        setupCoordinatorLayout();
        setupToolbar();
        setupPickers();
        setupViewPager();
        setupFloatingActionButton();

        // attach listener if dialog opened (post-configuration change)
        attachListenerToExistingFragment();

        FormComponent formComponent = ((EventCaptureApp) getApplication()).getFormComponent();

        // first time activity is created
        if (savedInstanceState == null) {
            // it means we found old component and we have to release it
            if (formComponent != null) {
                // create new instance of component
                ((EventCaptureApp) getApplication()).releaseFormComponent();
            }

            formComponent = ((EventCaptureApp) getApplication()).createFormComponent();
        } else {
            formComponent = ((EventCaptureApp) getApplication()).getFormComponent();
        }

        // if it is first time when FormSectionsActivity is
        // instantiated, we need to show DatePickerDialog
        if (savedInstanceState == null && isEventNew()) {
            showDatePickerDialog();
        }

        // inject dependencies
        formComponent.inject(this);

        // start building the form
        formSectionPresenter.createDataEntryForm(getEventUid());

        setupLocationPermissions();
    }

    @Override
    protected void onStart() {
        formSectionPresenter.attachView(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        //don't leak the dialog
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
        formSectionPresenter.detachView();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (sectionDialogFragment != null) {
            getMenuInflater().inflate(R.menu.menu_form_sections, menu);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                super.onBackPressed();
                return true;
            }
            case R.id.filter_button:
                if (sectionDialogFragment != null) {
                    sectionDialogFragment.show(getSupportFragmentManager(), FilterableDialogFragment.TAG);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setLocation(Location location) {
        setLocationButtonState(true);
        if (location != null) {
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();

            if (longitude != 0.0 && latitude != 0.0) {
                editTextLatitude.setText(String.format(Locale.getDefault(), "%1$,.6f", longitude));
                editTextLongitude.setText(String.format(Locale.getDefault(), "%1$,.6f", latitude));
            }
        } else {
            Toast.makeText(this, R.string.gps_no_coordinates, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void setLocationButtonState(boolean enabled) {
        if (enabled) {
            //re-enable the location fields and location button
            locationIcon.setVisibility(View.VISIBLE);
            locationIconCancel.setVisibility(View.GONE);
            locationProgressBar.setVisibility(View.GONE);
            locationButtonLayout.setClickable(true);
        } else {
            // disable it:
            locationIcon.setVisibility(View.GONE);
            locationIconCancel.setVisibility(View.VISIBLE);
            locationProgressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            List<String> permissionsList = Arrays.asList(permissions);
            int at = permissionsList.indexOf(Manifest.permission.ACCESS_FINE_LOCATION);
            if (at >= 0 && grantResults[at] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: permission is granged");
                // don't do anything
            } else if (at >= 0 && grantResults[at] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(FormSectionActivity.this,
                        R.string.gps_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Initialize the location permissions.
     */
    public void setupLocationPermissions() {
        Log.d(TAG, "setupLocationPermissions() called with: " + "");
        if (Build.VERSION.SDK_INT > 22 &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this, permissions, LOCATION_REQUEST_CODE);
        }
    }

    @Override
    public void showFormDefaultSection(String formSectionId) {
        FormSingleSectionAdapter viewPagerAdapter =
                new FormSingleSectionAdapter(getSupportFragmentManager());
        viewPagerAdapter.swapData(getEventUid(), formSectionId);

        // in order not to loose state of ViewPager, first we
        // have to fill FormSectionsAdapter with data, and only then set it to ViewPager
        viewPager.setAdapter(viewPagerAdapter);

        // hide tab layout
        tabLayout.setVisibility(View.GONE);

        // if we don't have sections, we don't need to show navigation drawer
        // sectionsDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);

        // we also need to hide filter icon
        supportInvalidateOptionsMenu();
    }

    @Override
    public void showFormSections(List<FormSection> formSections) {
        FormSectionsAdapter viewPagerAdapter =
                new FormSectionsAdapter(getSupportFragmentManager());
        viewPagerAdapter.swapData(getEventUid(), formSections);

        // in order not to loose state of ViewPager, first we
        // have to fill FormSectionsAdapter with data, and only then set it to ViewPager
        viewPager.setAdapter(viewPagerAdapter);

        // hide tab layout
        tabLayout.setVisibility(View.VISIBLE);

        // TabLayout will fail on you, if ViewPager which is going to be
        // attached does not contain ViewPagerAdapter set to it.
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public void setFormSectionsPicker(Picker picker) {
        sectionDialogFragment = FilterableDialogFragment.newInstance(picker);
        sectionDialogFragment.setOnPickerItemClickListener(new OnSearchSectionsClickListener());

        supportInvalidateOptionsMenu();
    }

    @Override
    public void showReportDatePicker(String hint, String value) {
        String dateLabel = isEmpty(hint) ? getString(R.string.report_date) : hint;
        textViewReportDate.setHint(dateLabel);

        if (!isEmpty(value)) {
            textViewReportDate.setText(String.format(Locale.getDefault(),
                    "%s: %s", dateLabel, value));
        }
    }

    @Override
    public void showCoordinatesPicker(String latitude, String longitude) {
        if (linearLayoutCoordinates.getVisibility() == View.INVISIBLE ||
                linearLayoutCoordinates.getVisibility() == View.GONE) {
            linearLayoutCoordinates.setVisibility(View.VISIBLE);
            setupLocationCallback();
        }
        if (!isEmpty(latitude)) {
            editTextLatitude.setText(latitude);
        }
        if (!isEmpty(longitude)) {
            editTextLongitude.setText(longitude);
        }
    }

    @Override
    public void showEventStatus(Event.EventStatus eventStatus) {
        if (fabComplete != null && eventStatus != null) {
            fabComplete.setVisibility(View.VISIBLE);
            fabComplete.setActivated(Event.EventStatus.COMPLETED.equals(eventStatus));
        }
    }

    @Override
    public String getFormSectionLabel(@FormSectionLabelId String formSectionLabelId) {
        switch (formSectionLabelId) {
            case FormSectionView.ID_CHOOSE_SECTION: {
                return getString(R.string.choose_section);
            }
        }

        return null;
    }

    private void attachListenerToExistingFragment() {
        FilterableDialogFragment dialogFragment = (FilterableDialogFragment)
                getSupportFragmentManager().findFragmentByTag(FilterableDialogFragment.TAG);

        // if we don't have fragment attached to activity,
        // we don't want to do anything else
        if (dialogFragment != null) {
            dialogFragment.setOnPickerItemClickListener(new OnSearchSectionsClickListener());
        }
    }

    private void setupCoordinatorLayout() {
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorlayout_form);
    }

    private void setupToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupPickers() {
        textViewReportDate = (TextView) findViewById(R.id.textview_report_date);
        linearLayoutCoordinates = (LinearLayout) findViewById(R.id.linearlayout_coordinates);
        editTextLatitude = (EditText) findViewById(R.id.edittext_latitude);
        editTextLongitude = (EditText) findViewById(R.id.edittext_longitude);
        locationIcon = (AppCompatImageView) findViewById(R.id.imagevew_location);
        locationIconCancel = (AppCompatImageView) findViewById(R.id.imagevew_location_cancel);
        locationProgressBar = (CircularProgressBar) findViewById(R.id.progress_bar_circular_location);
        locationButtonLayout = (FrameLayout) findViewById(R.id.button_location_layout);

        // set on click listener to text view report date
        textViewReportDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePickerDialog();
            }
        });

        // since coordinates are optional, initially they should be hidden
        linearLayoutCoordinates.setVisibility(View.GONE);
    }

    private void setupLocationCallback() {
        locationButtonLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocationManager locationManager = (LocationManager) getSystemService(
                        Context.LOCATION_SERVICE);
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

                    //we have permission ?
                    if (Build.VERSION.SDK_INT < 23 ||
                            ActivityCompat.checkSelfPermission(v.getContext(),
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED) {
                        //either if init or after cancel click:
                        if (locationIcon.getVisibility() == View.VISIBLE
                                || locationIconCancel.getVisibility() == View.GONE) {
                            // request location:
                            setLocationButtonState(false);
                            formSectionPresenter.subscribeToLocations();
                        } else {
                            //cancel the location request:
                            setLocationButtonState(true);
                            formSectionPresenter.stopLocationUpdates();
                        }
                    } else {
                        //don't have permissions, set them up !
                        setupLocationPermissions();
                    }
                } else {
                    showGpsDialog();
                }
            }
        });
    }

    public void showGpsDialog() {
        alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.title_gps_disabled)
                .setMessage(R.string.gps_disabled)
                .setPositiveButton(R.string.settings_option, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel_option, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .create();

        alertDialog.show();
    }

    private void setupViewPager() {
        tabLayout = (TabLayout) findViewById(R.id.tablayout_data_entry);
        viewPager = (ViewPager) findViewById(R.id.viewpager_dataentry);

        // hide tab layout initially in order to prevent UI
        // jumps in cases when we don't have sections
        tabLayout.setVisibility(View.GONE);
    }

    private void setupFloatingActionButton() {
        fabComplete = (FloatingActionButton) findViewById(R.id.fab_complete_event);
        fabComplete.setVisibility(View.GONE);

        fabComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isCompleted = fabComplete.isActivated();
                fabComplete.setActivated(!isCompleted);

                if (isCompleted) {
                    incompleteEvent();

                    Snackbar.make(coordinatorLayout, getString(R.string.incomplete), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo), new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    completeEvent();
                                    fabComplete.setActivated(true);
                                }
                            })
                            .show();
                } else {
                    completeEvent();
                    Snackbar.make(coordinatorLayout, getString(R.string.complete), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo), new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    incompleteEvent();
                                    fabComplete.setActivated(false);
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void incompleteEvent() {
        formSectionPresenter.saveEventStatus(getEventUid(), Event.EventStatus.ACTIVE);
    }

    private void completeEvent() {
        formSectionPresenter.saveEventStatus(getEventUid(), Event.EventStatus.COMPLETED);
    }

    private class OnSearchSectionsClickListener implements OnPickerItemClickListener {
        @Override
        public void onPickerItemClickListener(Picker selectedPicker) {
            PagerAdapter pagerAdapter = viewPager.getAdapter();

            if (pagerAdapter != null && (pagerAdapter instanceof FormSectionsAdapter)) {
                FormSectionsAdapter sectionsAdapter = (FormSectionsAdapter) pagerAdapter;
                List<FormSection> formSections = sectionsAdapter.getData();

                for (int position = 0; position < formSections.size(); position++) {
                    FormSection formSection = formSections.get(position);

                    if (formSection.getId().equals(selectedPicker.getId())) {
                        viewPager.setCurrentItem(position);
                        break;
                    }
                }
            }
        }
    }

    private void showDatePickerDialog() {
        final OnDateSetListener onDateSetListener = new OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, monthOfYear);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                String stringDate = (new SimpleDateFormat(DATE_FORMAT, Locale.US))
                        .format(calendar.getTime());
                String newValue = String.format(Locale.getDefault(), "%s: %s",
                        getString(R.string.report_date), stringDate);
                textViewReportDate.setText(newValue);

                DateTime currentDateTime = DateTime.now();
                DateTime selectedDateTime = DateTime.parse(stringDate);

                /*
                * in case when user selected today's date, we need to know about time as well.
                * selectedDateTime does not contain time information (only date), that's why we
                * need to create a new DateTime object by calling DateTime.now()
                */
                DateTime dateTime;
                if (DateTimeComparator.getDateOnlyInstance()
                        .compare(currentDateTime, selectedDateTime) == 0) {
                    dateTime = currentDateTime;
                } else {
                    dateTime = selectedDateTime;
                }

                formSectionPresenter.saveEventDate(getEventUid(), dateTime);
            }
        };

        DatePickerDialogFragment datePickerDialogFragment =
                DatePickerDialogFragment.newInstance(false);
        datePickerDialogFragment.setOnDateSetListener(onDateSetListener);
        datePickerDialogFragment.show(getSupportFragmentManager());
    }

    /*
    *
    * This adapter exists only in order to satisfy cases when there is no
    * sections assigned to program stage. As the result, we have to
    * use program stage itself as section
    *
    */
    private static class FormSingleSectionAdapter extends FragmentStatePagerAdapter {
        private static final int DEFAULT_STAGE_COUNT = 1;
        private static final int DEFAULT_STAGE_POSITION = 0;
        private String eventId;
        private String formSectionId;

        public FormSingleSectionAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            if (DEFAULT_STAGE_POSITION == position && !isEmpty(formSectionId)) {
                return DataEntryFragment.newInstanceForStage(eventId, formSectionId);
            }

            return null;
        }

        @Override
        public int getCount() {
            return isEmpty(formSectionId) ? 0 : DEFAULT_STAGE_COUNT;
        }

        public void swapData(String eventId, String programStageId) {
            this.eventId = eventId;
            this.formSectionId = programStageId;
            this.notifyDataSetChanged();
        }
    }

    private static class FormSectionsAdapter extends FragmentStatePagerAdapter {
        private final List<FormSection> formSections;
        private String eventId;

        public FormSectionsAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            this.formSections = new ArrayList<>();
        }

        @Override
        public Fragment getItem(int position) {
            FormSection formSection = formSections.get(position);
            return DataEntryFragment.newInstanceForSection(eventId, formSection.getId());
        }

        @Override
        public int getCount() {
            return formSections.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            FormSection formSection = formSections.get(position);
            return formSection.getLabel();
        }

        @NonNull
        public List<FormSection> getData() {
            return formSections;
        }

        public void swapData(String eventId, List<FormSection> formSections) {
            this.eventId = eventId;
            this.formSections.clear();

            if (formSections != null) {
                this.formSections.addAll(formSections);
            }

            notifyDataSetChanged();
        }
    }
}
