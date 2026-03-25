import { render, screen, fireEvent, act } from '@testing-library/react';
import { StreamUrlDisplay } from './StreamUrlDisplay';

const TEST_URL = 'http://192.168.1.100:8080/';

describe('StreamUrlDisplay', () => {
  it('renders the stream URL', () => {
    render(<StreamUrlDisplay url={TEST_URL} />);
    expect(screen.getByText(TEST_URL)).toBeInTheDocument();
  });

  it('shows WiFi network context with SSID', () => {
    render(
      <StreamUrlDisplay url={TEST_URL} connectionType="wifi" networkSsid="HomeNetwork" />
    );
    expect(screen.getByText(/OBS must be on/)).toBeInTheDocument();
    expect(screen.getByText('HomeNetwork')).toBeInTheDocument();
  });

  it('shows hotspot connection instructions', () => {
    render(<StreamUrlDisplay url={TEST_URL} connectionType="hotspot" />);
    expect(screen.getByText(/Connect your OBS computer to your phone.s hotspot/)).toBeInTheDocument();
  });

  it('shows no network context when connectionType is none', () => {
    render(<StreamUrlDisplay url={TEST_URL} connectionType="none" />);
    expect(screen.queryByText(/OBS must be on/)).not.toBeInTheDocument();
    expect(screen.queryByText(/hotspot/i)).not.toBeInTheDocument();
  });

  it('shows no network context when connectionType is omitted', () => {
    render(<StreamUrlDisplay url={TEST_URL} />);
    expect(screen.queryByText(/OBS must be on/)).not.toBeInTheDocument();
  });

  it('copies URL to clipboard when copy button is clicked', async () => {
    Object.assign(navigator, {
      clipboard: { writeText: jest.fn().mockResolvedValue(undefined) },
    });

    render(<StreamUrlDisplay url={TEST_URL} />);
    await act(async () => { fireEvent.click(screen.getByText('Copy URL')); });

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(TEST_URL);
  });

  it('shows "Copied!" feedback after copying', async () => {
    Object.assign(navigator, {
      clipboard: { writeText: jest.fn().mockResolvedValue(undefined) },
    });

    render(<StreamUrlDisplay url={TEST_URL} />);
    await act(async () => { fireEvent.click(screen.getByText('Copy URL')); });

    expect(screen.getByText('✓ Copied!')).toBeInTheDocument();
  });

  it('hides copy button when enableCopy is false', () => {
    render(<StreamUrlDisplay url={TEST_URL} enableCopy={false} />);
    expect(screen.queryByText('Copy URL')).not.toBeInTheDocument();
  });
});
