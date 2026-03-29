import { render, screen, fireEvent } from '@testing-library/react';
import { ResolutionPicker } from './ResolutionPicker';

describe('ResolutionPicker', () => {
  it('renders all three resolution options', () => {
    render(<ResolutionPicker value="720p" onChange={jest.fn()} />);
    expect(screen.getByText('480p')).toBeInTheDocument();
    expect(screen.getByText('720p')).toBeInTheDocument();
    expect(screen.getByText('1080p')).toBeInTheDocument();
  });

  it('marks the selected option as active via aria-pressed', () => {
    render(<ResolutionPicker value="720p" onChange={jest.fn()} />);
    const button720 = screen.getByText('720p').closest('button')!;
    const button480 = screen.getByText('480p').closest('button')!;
    expect(button720).toHaveAttribute('aria-pressed', 'true');
    expect(button480).toHaveAttribute('aria-pressed', 'false');
  });

  it('applies active class to selected option only', () => {
    render(<ResolutionPicker value="480p" onChange={jest.fn()} />);
    const button480 = screen.getByText('480p').closest('button')!;
    const button720 = screen.getByText('720p').closest('button')!;
    expect(button480).toHaveClass('resolution-option--active');
    expect(button720).not.toHaveClass('resolution-option--active');
  });

  it('calls onChange with the correct preset when an option is clicked', () => {
    const onChange = jest.fn();
    render(<ResolutionPicker value="720p" onChange={onChange} />);
    fireEvent.click(screen.getByText('480p'));
    expect(onChange).toHaveBeenCalledWith('480p');
  });

  it('calls onChange with 1080p when 1080p is clicked', () => {
    const onChange = jest.fn();
    render(<ResolutionPicker value="720p" onChange={onChange} />);
    fireEvent.click(screen.getByText('1080p'));
    expect(onChange).toHaveBeenCalledWith('1080p');
  });

  it('does not call onChange when disabled', () => {
    const onChange = jest.fn();
    render(<ResolutionPicker value="720p" onChange={onChange} disabled />);
    fireEvent.click(screen.getByText('480p'));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('disables all buttons when disabled prop is true', () => {
    render(<ResolutionPicker value="720p" onChange={jest.fn()} disabled />);
    screen.getAllByRole('button').forEach(btn => {
      expect(btn).toBeDisabled();
    });
  });

  it('has an accessible group label', () => {
    render(<ResolutionPicker value="720p" onChange={jest.fn()} />);
    expect(screen.getByRole('group', { name: /streaming resolution/i })).toBeInTheDocument();
  });
});
