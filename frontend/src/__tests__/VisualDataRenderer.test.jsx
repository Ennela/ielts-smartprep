import '@testing-library/jest-dom';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import VisualDataRenderer from '../components/writing/VisualDataRenderer';

/*
 * In the narrow prompt column the chart lost text: the rotated y-axis title was clipped and
 * pie labels outside the pie were cut off. Axis titles are now a caption above the chart. A
 * map or process step that is an object (Gemini output is not validated) used to make React
 * throw, so the chart vanished.
 */

describe('VisualDataRenderer', () => {
  it('shows the axis titles as a caption above a bar or line chart', () => {
    render(<VisualDataRenderer essayType="BAR_CHART" visualDataJson={JSON.stringify({
      title: 'Enrolment', xAxisLabel: 'Course', yAxisLabel: 'Adults', xAxisKey: 'course',
      keys: ['2010'], data: [{ course: 'IT', 2010: 5 }],
    })} />);

    expect(screen.getByText('Enrolment')).toBeInTheDocument();
    expect(screen.getByText('Vertical axis: Adults')).toBeInTheDocument();
    expect(screen.getByText('Horizontal axis: Course')).toBeInTheDocument();
  });

  it('draws map features and process steps given as objects instead of failing', () => {
    const { unmount } = render(<VisualDataRenderer essayType="MAP" visualDataJson={JSON.stringify({
      title: 'Village', map1: [{ feature: 'Harbour', location: 'south' }], map2: ['Marina in the south'],
    })} />);
    expect(screen.getByText('Harbour – south')).toBeInTheDocument();
    expect(screen.getByText('Marina in the south')).toBeInTheDocument();
    unmount();

    render(<VisualDataRenderer essayType="DIAGRAM" visualDataJson={JSON.stringify({
      title: 'Process', steps: [{ step: 1, description: 'Rain falls' }, 'Water is filtered'],
    })} />);
    expect(screen.getByText('1 – Rain falls')).toBeInTheDocument();
    expect(screen.getByText('Water is filtered')).toBeInTheDocument();
  });
});
