import { render, screen, fireEvent } from '@testing-library/react';
import { StartStreamingButton } from './StartStreamingButton';

describe('StartStreamingButton', () => {
  it('renders Start Streaming text in idle state', () => {
    render(<StartStreamingButton onStart={jest.fn()} />);
    expect(screen.getByText('Start Streaming')).toBeInTheDocument();
  });

  it('calls onStart when clicked', () => {
    const onStart = jest.fn();
    render(<StartStreamingButton onStart={onStart} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onStart).toHaveBeenCalledTimes(1);
  });

  it('shows Starting... and disables button when isStarting is true', () => {
    render(<StartStreamingButton onStart={jest.fn()} isStarting={true} />);
    expect(screen.getByText('Starting...')).toBeInTheDocument();
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('does not call onStart when disabled', () => {
    const onStart = jest.fn();
    render(<StartStreamingButton onStart={onStart} disabled={true} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onStart).not.toHaveBeenCalled();
  });

  it('has accessible aria-label', () => {
    render(<StartStreamingButton onStart={jest.fn()} />);
    expect(screen.getByRole('button', { name: /Start streaming to OBS/i })).toBeInTheDocument();
  });
});
