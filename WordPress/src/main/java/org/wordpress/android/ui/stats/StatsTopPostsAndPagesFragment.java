package org.wordpress.android.ui.stats;

import android.os.Bundle;
import android.widget.ArrayAdapter;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.ui.stats.adapters.PostsAndPagesAdapter;
import org.wordpress.android.ui.stats.models.StatsPostModel;
import org.wordpress.android.ui.stats.models.TopPostsAndPagesModel;
import org.wordpress.android.ui.stats.service.StatsServiceLogic;

import java.util.ArrayList;
import java.util.List;


public class StatsTopPostsAndPagesFragment extends StatsAbstractListFragment {
    public static final String TAG = StatsTopPostsAndPagesFragment.class.getSimpleName();

    private TopPostsAndPagesModel mTopPostsAndPagesModel = null;

    @Override
    protected boolean hasDataAvailable() {
        return mTopPostsAndPagesModel != null;
    }

    @Override
    protected void saveStatsData(Bundle outState) {
        if (hasDataAvailable()) {
            outState.putSerializable(ARG_REST_RESPONSE, mTopPostsAndPagesModel);
        }
    }

    @Override
    protected void restoreStatsData(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(ARG_REST_RESPONSE)) {
            mTopPostsAndPagesModel = (TopPostsAndPagesModel) savedInstanceState.getSerializable(ARG_REST_RESPONSE);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(StatsEvents.TopPostsUpdated event) {
        if (!shouldUpdateFragmentOnUpdateEvent(event)) {
            return;
        }

        mGroupIdToExpandedMap.clear();
        mTopPostsAndPagesModel = event.mTopPostsAndPagesModel;

        updateUI();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(StatsEvents.SectionUpdateError event) {
        if (!shouldUpdateFragmentOnErrorEvent(event)) {
            return;
        }

        mTopPostsAndPagesModel = null;
        mGroupIdToExpandedMap.clear();
        showErrorUI(event.mError);
    }

    @Override
    protected void updateUI() {
        if (!isAdded()) {
            return;
        }

        if (hasTopPostsAndPages()) {
            List<StatsPostModel> postViews = mTopPostsAndPagesModel.getTopPostsAndPages();
            ArrayAdapter adapter = new PostsAndPagesAdapter(getActivity(), postViews, false);
            StatsUIHelper.reloadLinearLayout(getActivity(), adapter, mList, getMaxNumberOfItemsToShowInList());
            showHideNoResultsUI(false);
        } else {
            showHideNoResultsUI(true);
        }
    }

    private boolean hasTopPostsAndPages() {
        return mTopPostsAndPagesModel != null && mTopPostsAndPagesModel.hasTopPostsAndPages();
    }

    private List<StatsPostModel> getTopPostsAndPages() {
        if (!hasTopPostsAndPages()) {
            return new ArrayList<StatsPostModel>(0);
        }
        return mTopPostsAndPagesModel.getTopPostsAndPages();
    }

    @Override
    protected boolean isViewAllOptionAvailable() {
        return hasTopPostsAndPages() && getTopPostsAndPages().size() > MAX_NUM_OF_ITEMS_DISPLAYED_IN_LIST;
    }

    @Override
    protected boolean isExpandableList() {
        return false;
    }

    @Override
    protected int getEntryLabelResId() {
        return R.string.stats_entry_posts_and_pages;
    }

    @Override
    protected int getTotalsLabelResId() {
        return R.string.stats_totals_views;
    }

    @Override
    protected int getEmptyLabelTitleResId() {
        return R.string.stats_empty_top_posts_title;
    }

    @Override
    protected int getEmptyLabelDescResId() {
        return R.string.stats_empty_top_posts_desc;
    }

    @Override
    protected StatsServiceLogic.StatsEndpointsEnum[] sectionsToUpdate() {
        return new StatsServiceLogic.StatsEndpointsEnum[]{
                StatsServiceLogic.StatsEndpointsEnum.TOP_POSTS
        };
    }

    @Override
    public String getTitle() {
        return getString(R.string.stats_view_top_posts_and_pages);
    }
}
