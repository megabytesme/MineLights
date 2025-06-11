using System.Diagnostics;
using System.Reflection;
using MethodInvoker = System.Windows.Forms.MethodInvoker;

class Program
{
    private static Mutex mutex = new Mutex(true, "{8E2D4B6C-7F01-4A5D-9C2E-2A47A5A0A9A3}");

    [STAThread]
    static void Main(string[] args)
    {
        Console.SetOut(ServerLogger.Instance);

        if (!mutex.WaitOne(TimeSpan.Zero, true))
        {
            MessageBox.Show("MineLights Server is already running.", "MineLights", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        NativeDllLoader.LoadNativeSDKs();

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new MyAppContext());

        mutex.ReleaseMutex();
        GC.KeepAlive(mutex);
    }
}

public class MyAppContext : ApplicationContext
{
    private NotifyIcon trayIcon;
    private LightingServer lightingServer;

    public MyAppContext()
    {
        lightingServer = new LightingServer(Shutdown);
        lightingServer.Start();

        var iconStream = Assembly.GetExecutingAssembly().GetManifestResourceStream("MineLightsV2.resources.app_icon.ico");
        if (iconStream == null) throw new FileNotFoundException("Tray icon resource not found.");

        trayIcon = new NotifyIcon()
        {
            Icon = new Icon(iconStream),
            Text = "MineLights Server",
            ContextMenuStrip = new ContextMenuStrip(),
            Visible = true
        };

        trayIcon.ContextMenuStrip.Items.Add("View Logs", null, OnViewLogs);
        trayIcon.ContextMenuStrip.Items.Add(new ToolStripSeparator());
        trayIcon.ContextMenuStrip.Items.Add("Exit", null, OnExit);
    }

    private void OnViewLogs(object? sender, EventArgs e)
    {
        try
        {
            string logPath = ServerLogger.Instance.LogFilePath;
            if (File.Exists(logPath))
            {
                Process.Start(new ProcessStartInfo(logPath) { UseShellExecute = true });
            }
            else
            {
                MessageBox.Show("Log file not found.", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Could not open log file.\n\nError: {ex.Message}", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void Shutdown()
    {
        if (trayIcon.ContextMenuStrip?.InvokeRequired ?? false)
        {
            trayIcon.ContextMenuStrip.Invoke(new MethodInvoker(Shutdown));
        }
        else
        {
            lightingServer.Stop();
            trayIcon.Visible = false;
            Application.Exit();
        }
    }

    void OnExit(object? sender, EventArgs e)
    {
        Shutdown();
    }
}