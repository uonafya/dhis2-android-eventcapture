package org.hisp.dhis.android.eventcapture.presenters;

import org.hisp.dhis.android.eventcapture.views.RxOnValueChangedListener;
import org.hisp.dhis.android.eventcapture.views.View;
import org.hisp.dhis.android.eventcapture.views.fragments.ProfileView;
import org.hisp.dhis.client.sdk.android.user.UserAccountInteractor;
import org.hisp.dhis.client.sdk.models.user.UserAccount;
import org.hisp.dhis.client.sdk.ui.models.FormEntity;
import org.hisp.dhis.client.sdk.ui.models.FormEntityCharSequence;
import org.hisp.dhis.client.sdk.ui.models.FormEntityDate;
import org.hisp.dhis.client.sdk.ui.models.FormEntityEditText;
import org.hisp.dhis.client.sdk.ui.models.FormEntityEditText.InputType;
import org.hisp.dhis.client.sdk.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static org.hisp.dhis.client.sdk.utils.Preconditions.isNull;

public class ProfilePresenterImpl implements ProfilePresenter {
    private static final String TAG = ProfilePresenter.class.getSimpleName();

    // callback which will be called when values change in view
    private final RxOnValueChangedListener onFormEntityChangeListener;
    private final UserAccountInteractor userAccountInteractor;
    private final Logger logger;

    private ProfileView profileView;
    private CompositeSubscription subscription;
    private UserAccount userAccount;

    public ProfilePresenterImpl(UserAccountInteractor userAccountInteractor, Logger logger) {
        this.onFormEntityChangeListener = new RxOnValueChangedListener();
        this.userAccountInteractor = userAccountInteractor;
        this.logger = logger;
    }

    @Override
    public void createUserAccountForm() {
        logger.d(TAG, "createUserAccountForm()");

        // kill previous subscription
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }

        // create a new one
        subscription = new CompositeSubscription();
        subscription.add(userAccountInteractor.account()
                .map(new Func1<UserAccount, List<FormEntity>>() {
                    @Override
                    public List<FormEntity> call(UserAccount userAccount) {
                        return transformUserAccount(userAccount);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<List<FormEntity>>() {
                    @Override
                    public void call(List<FormEntity> entities) {
                        if (profileView != null) {
                            profileView.showUserAccountForm(entities);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.d(TAG, throwable.getMessage(), throwable);
                    }
                }));

        // listening to events which UI emits, save them into database
        subscription.add(Observable.create(onFormEntityChangeListener)
                .debounce(512, TimeUnit.MILLISECONDS)
                .switchMap(new Func1<FormEntity, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(FormEntity formEntity) {
                        return onFormEntityChanged(formEntity);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean isSaved) {
                        logger.d(TAG, String.format("UserAccount is saved: %s", isSaved));
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, throwable.getMessage(), throwable);
                    }
                }));
    }

    @Override
    public void attachView(View view) {
        isNull(view, "View must not be null");
        profileView = (ProfileView) view;

        // list account fields as soon as
        // presenter is attached to fragment
        createUserAccountForm();
    }

    @Override
    public void detachView() {
        profileView = null;

        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private List<FormEntity> transformUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;

        isNull(this.userAccount, "userAccount must not be null");
        isNull(this.profileView, "profileView must not be null");

        List<FormEntity> formEntities = new ArrayList<>();

        FormEntityEditText firstName = new FormEntityEditText(ProfileView.ID_FIRST_NAME,
                profileView.getUserAccountFieldLabel(ProfileView.ID_FIRST_NAME), InputType.TEXT);
        firstName.setValue(userAccount.getFirstName());
        firstName.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(firstName);

        FormEntityEditText surname = new FormEntityEditText(ProfileView.ID_SURNAME,
                profileView.getUserAccountFieldLabel(ProfileView.ID_SURNAME), InputType.TEXT);
        surname.setValue(userAccount.getSurname());
        surname.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(surname);

        // TODO add gender (Filterable Dialog, take a look into FilterableDialog and Pickers)

        FormEntityDate birthday = new FormEntityDate(ProfileView.ID_BIRTHDAY,
                profileView.getUserAccountFieldLabel(ProfileView.ID_BIRTHDAY));
        birthday.setValue(userAccount.getBirthday());
        birthday.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(birthday);

        FormEntityEditText introduction = new FormEntityEditText(ProfileView.ID_INTRODUCTION,
                profileView.getUserAccountFieldLabel(ProfileView.ID_INTRODUCTION), InputType.TEXT);
        introduction.setValue(userAccount.getIntroduction());
        introduction.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(introduction);

        FormEntityEditText education = new FormEntityEditText(ProfileView.ID_EDUCATION,
                profileView.getUserAccountFieldLabel(ProfileView.ID_EDUCATION), InputType.TEXT);
        education.setValue(userAccount.getEducation());
        education.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(education);

        FormEntityEditText employer = new FormEntityEditText(ProfileView.ID_EMPLOYER,
                profileView.getUserAccountFieldLabel(ProfileView.ID_EMPLOYER), InputType.TEXT);
        employer.setValue(userAccount.getEmployer());
        employer.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(employer);

        FormEntityEditText interests = new FormEntityEditText(ProfileView.ID_INTERESTS,
                profileView.getUserAccountFieldLabel(ProfileView.ID_INTERESTS), InputType.TEXT);
        interests.setValue(userAccount.getInterests());
        interests.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(interests);

        FormEntityEditText jobTitle = new FormEntityEditText(ProfileView.ID_JOB_TITLE,
                profileView.getUserAccountFieldLabel(ProfileView.ID_JOB_TITLE), InputType.TEXT);
        jobTitle.setValue(userAccount.getJobTitle());
        jobTitle.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(jobTitle);

        FormEntityEditText languages = new FormEntityEditText(ProfileView.ID_LANGUAGES,
                profileView.getUserAccountFieldLabel(ProfileView.ID_LANGUAGES), InputType.TEXT);
        languages.setValue(userAccount.getLanguages());
        languages.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(languages);

        FormEntityEditText email = new FormEntityEditText(ProfileView.ID_EMAIL,
                profileView.getUserAccountFieldLabel(ProfileView.ID_EMAIL), InputType.TEXT);
        email.setValue(userAccount.getEmail());
        email.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(email);

        FormEntityEditText phoneNumber = new FormEntityEditText(ProfileView.ID_PHONE_NUMBER,
                profileView.getUserAccountFieldLabel(ProfileView.ID_PHONE_NUMBER), InputType.TEXT);
        phoneNumber.setValue(userAccount.getPhoneNumber());
        phoneNumber.setOnFormEntityChangeListener(onFormEntityChangeListener);
        formEntities.add(phoneNumber);

        return formEntities;
    }

    private Observable<Boolean> onFormEntityChanged(FormEntity formEntity) {
        if (userAccount == null) {
            logger.e(TAG, "onFormEntityChanged() is called without UserAccount");
            throw new IllegalArgumentException("No UserAccount instance is found");
        }

        FormEntityCharSequence formEntityCharSequence = (FormEntityCharSequence) formEntity;
        String value = formEntityCharSequence.getValue().toString();

        logger.d(TAG, String.format("New value '%s' is emitted for field: '%s'",
                value, formEntityCharSequence.getLabel()));

        switch (formEntityCharSequence.getId()) {
            case ProfileView.ID_FIRST_NAME: {
                userAccount.setFirstName(value);
                break;
            }
            case ProfileView.ID_SURNAME: {
                userAccount.setSurname(value);
                break;
            }
            case ProfileView.ID_BIRTHDAY: {
                userAccount.setBirthday(value);
                break;
            }
            case ProfileView.ID_INTRODUCTION: {
                userAccount.setIntroduction(value);
                break;
            }
            case ProfileView.ID_EDUCATION: {
                userAccount.setEducation(value);
                break;
            }
            case ProfileView.ID_EMPLOYER: {
                userAccount.setEmployer(value);
                break;
            }
            case ProfileView.ID_INTERESTS: {
                userAccount.setInterests(value);
                break;
            }
            case ProfileView.ID_JOB_TITLE: {
                userAccount.setJobTitle(value);
                break;
            }
            case ProfileView.ID_LANGUAGES: {
                userAccount.setLanguages(value);
                break;
            }
            case ProfileView.ID_EMAIL: {
                userAccount.setEmail(value);
                break;
            }
            case ProfileView.ID_PHONE_NUMBER: {
                userAccount.setPhoneNumber(value);
                break;
            }
        }

        return userAccountInteractor.save(userAccount);
    }
}
