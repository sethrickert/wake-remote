using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;
using Application = System.Windows.Application;

namespace WakeRemote;

/// <summary>
/// Loads packaged art. Never resolves against the current working directory: launching
/// from the Start menu shortcut or the HKCU Run key leaves the working directory at
/// system32, which is what previously made the tray-icon load throw during construction
/// and kill the app before any window appeared.
/// </summary>
static class Assets
{
    static Stream? Open(string name)
    {
        // Embedded copy first, so a single-file portable exe needs nothing beside it.
        try
        {
            var info = Application.GetResourceStream(new Uri($"pack://application:,,,/Assets/{name}", UriKind.Absolute));
            if (info?.Stream is not null) return info.Stream;
        }
        catch { /* fall through to disk */ }

        // Then next to the executable, for the installed layout.
        try
        {
            var path = Path.Combine(AppContext.BaseDirectory, "Assets", name);
            if (File.Exists(path)) return File.OpenRead(path);
        }
        catch { /* fall through to null */ }

        return null;
    }

    /// <summary>The tray icon, or the Windows default if the asset cannot be loaded.</summary>
    public static System.Drawing.Icon TrayIcon(string name)
    {
        try
        {
            using var stream = Open(name);
            if (stream is not null) return new System.Drawing.Icon(stream, new System.Drawing.Size(32, 32));
        }
        catch { /* fall through to the stock icon */ }

        // A missing asset must never prevent the tray icon, and therefore the app, existing.
        return System.Drawing.SystemIcons.Application;
    }

    /// <summary>Bitmap art, or null when unavailable. Callers must tolerate null.</summary>
    public static BitmapImage? Bitmap(string name)
    {
        try
        {
            using var stream = Open(name);
            if (stream is null) return null;
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;  // decode now; the stream is disposed below
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch
        {
            return null;
        }
    }
}
