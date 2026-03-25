import { render, screen } from '@testing-library/react';
import { BatteryWarning } from './BatteryWarning';

describe('BatteryWarning', () => {
  it('shows warning when battery is below threshold and not charging', () => {
    render(<BatteryWarning batteryLevel={15} isCharging={false} />);
    expect(screen.getByText('15%')).toBeInTheDocument();
    expect(screen.getByText(/Battery low/)).toBeInTheDocument();
  });

  it('is hidden when charging, even with low battery', () => {
    const { container } = render(<BatteryWarning batteryLevel={10} isCharging={true} />);
    expect(container.firstChild).toBeNull();
  });

  it('is hidden when battery is above threshold', () => {
    const { container } = render(<BatteryWarning batteryLevel={50} isCharging={false} />);
    expect(container.firstChild).toBeNull();
  });

  it('shows critical battery icon below 10%', () => {
    render(<BatteryWarning batteryLevel={5} isCharging={false} />);
    expect(screen.getByText('🪫')).toBeInTheDocument();
  });

  it('shows standard battery icon between 10-19%', () => {
    render(<BatteryWarning batteryLevel={15} isCharging={false} />);
    expect(screen.getByText('🔋')).toBeInTheDocument();
  });

  it('respects custom threshold', () => {
    // Level 15 is above custom threshold of 10 — should not show
    const { container } = render(
      <BatteryWarning batteryLevel={15} isCharging={false} threshold={10} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('is hidden when showWarning is false', () => {
    const { container } = render(
      <BatteryWarning batteryLevel={5} isCharging={false} showWarning={false} />
    );
    expect(container.firstChild).toBeNull();
  });
});
