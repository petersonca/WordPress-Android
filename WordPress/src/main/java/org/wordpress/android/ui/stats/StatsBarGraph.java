package org.wordpress.android.ui.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;

import com.jjoe64.graphview.CustomLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewDataInterface;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;

import org.wordpress.android.R;

import java.util.LinkedList;
import java.util.List;

/**
 * A Bar graph depicting the view and visitors.
 * Based on BarGraph from the GraphView library.
 */
class StatsBarGraph extends GraphView {
    // Keep tracks of every bar drawn on the graph.
    private  List<List<BarChartRect>> seriesOnScreen = (List<List<BarChartRect>>) new LinkedList();
    private int barPositionToHighlight = -1;

	public StatsBarGraph(Context context) {
		super(context, "");

        setProperties();
	}

	private void setProperties() {
        getGraphViewStyle().setHorizontalLabelsColor(Color.BLACK);
        getGraphViewStyle().setVerticalLabelsColor(Color.BLACK);
        getGraphViewStyle().setTextSize(getResources().getDimensionPixelSize(R.dimen.graph_font_size));
        getGraphViewStyle().setGridXColor(Color.TRANSPARENT);
        getGraphViewStyle().setGridYColor(getResources().getColor(R.color.stats_bar_graph_grid));
        getGraphViewStyle().setNumVerticalLabels(6);

        setCustomLabelFormatter(new CustomLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if(isValueX)
                    return null;

                if (value < 1000) {
                    return null;
                } else if (value < 1000000) { // thousands
                    return Math.round(value / 1000) + "K";
                } else if (value < 1000000000) { // millions
                    return Math.round(value / 1000000) + "M";
                } else {
                    return null;
                }
            }
        });
    }


    /**
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        seriesOnScreen.clear(); // Empty the list before calling super. Super calls drawSeries and we need an empty list there.
        super.onDraw(canvas);
    }

    @Override
	public void drawSeries(Canvas canvas, GraphViewDataInterface[] values,
			float graphwidth, float graphheight, float border, double minX,
			double minY, double diffX, double diffY, float horstart,
			GraphViewSeriesStyle style) {
		float colwidth = graphwidth / values.length;

		paint.setStrokeWidth(style.thickness);
		paint.setColor(style.color);

        List<BarChartRect> barChartRects = new LinkedList<BarChartRect>(); // Bar chart position of this series on the canvas

		// draw data
		for (int i = 0; i < values.length; i++) {
			float valY = (float) (values[i].getY() - minY);
			float ratY = (float) (valY / diffY);
			float y = graphheight * ratY;

			// hook for value dependent color
			if (style.getValueDependentColor() != null) {
				paint.setColor(style.getValueDependentColor().get(values[i]));
			}

            //Trick to redraw the tapped bar
            if (barPositionToHighlight == i) {
                int color;
                if (style.color == getResources().getColor(R.color.stats_bar_graph_views)) {
                    color =  getResources().getColor(R.color.orange_medium);
                } else {
                    color = getResources().getColor(R.color.orange_dark);
                }
                paint.setColor(color);
            } else {
                paint.setColor(style.color);
            }

			float pad = style.padding;

			float left = (i * colwidth) + horstart;
			float top = (border - y) + graphheight;
			float right = left + colwidth;
			float bottom = graphheight + border - 1;

			canvas.drawRect(left + pad, top, right - pad, bottom, paint);
            barChartRects.add(new BarChartRect(left + pad, top, right - pad, bottom));
		}
        seriesOnScreen.add(barChartRects);
	}

    public int getTappedBar() {
        float[] lastTouchEventPoint = this.getLastTouchPointAndReset();
        for (List<BarChartRect> barChartRects : seriesOnScreen) {
            int i = 0;
            for (BarChartRect barChartRect : barChartRects) {
                if (barChartRect.isPointInside(lastTouchEventPoint[0], lastTouchEventPoint[1])) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    public void highlightBar(int barPosition) {
        barPositionToHighlight = barPosition;
        this.redrawAll();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                barPositionToHighlight = -1;
                redrawAll();
            }
        }, 500);
    }

	@Override
	protected double getMinY() {
		return 0;
	}

	@Override
	protected double getMaxY() {
		double maxY = super.getMaxY();

		final int divideBy;
		if (maxY < 100)
			divideBy = 10;
		else if (maxY < 1000)
			divideBy = 100;
		else if (maxY < 10000)
			divideBy = 1000;
		else if (maxY < 100000)
			divideBy = 10000;
		else if (maxY < 1000000)
			divideBy = 100000;
		else
			divideBy = 1000000;

		maxY = Math.rint((maxY / divideBy) + 1) * divideBy;
		return maxY;
	}


    /**
     * Private class that is used to hold the local (to the canvas) coordinate on the screen of every single bar in the graph
     */
    private class BarChartRect {
        float left, top, right, bottom;

        BarChartRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean isPointInside(float x, float y) {
            if (x >= this.left && x <= this.right &&
                    y<= this.bottom && y>= this.top) {
                return true;
            }
            return false;
        }
    }
}
