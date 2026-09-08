using System.Windows;
using MessageBox = System.Windows.MessageBox;

namespace WakeRemote;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Anything that escapes an event handler used to take the whole app down with a
        // raw .NET fault dialog. Show something readable and keep running instead.
        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show(args.Exception.Message, "Wake Remote", MessageBoxButton.OK, MessageBoxImage.Warning);
            args.Handled = true;
        };

        try
        {
            var window = new MainWindow();
            MainWindow = window;
            window.Show();
        }
        catch (Exception ex)
        {
            // A window that cannot be constructed is fatal, but it should say why rather
            // than vanishing with no window and no tray icon.
            MessageBox.Show($"Wake Remote could not start.\n\n{ex}", "Wake Remote",
                MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }
}
