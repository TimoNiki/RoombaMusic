import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.StringJoiner;

public class RoombaMusic extends JFrame {
    private SerialPort serialPort;
    private JComboBox<String> portList;
    private JButton btnConnect, btnOpenFile;
    private JLabel statusLabel;
    private JTextArea logArea;

    public RoombaMusic() {
        setTitle("RoombaMusic V1 | By TimoNiki");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        
        JPanel topPanel = new JPanel(new FlowLayout());
        portList = new JComboBox<>(SerialPortList.getPortNames());
        topPanel.add(portList);

        btnConnect = new JButton("1. Подключить");
        btnConnect.addActionListener(e -> connect());
        topPanel.add(btnConnect);

        btnOpenFile = new JButton("2. Выбрать .midi и играть");
        btnOpenFile.setEnabled(false);
        btnOpenFile.addActionListener(e -> chooseAndPlayMidi());
        topPanel.add(btnOpenFile);
     
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel("Waiting Connection to Roomba..", SwingConstants.CENTER);
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("AppLogs:"));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void connect() {
        try {
            serialPort = new SerialPort((String) portList.getSelectedItem());
            serialPort.openPort();
            serialPort.setParams(115200, 8, 1, 0);
            
            // ROI: Start (128) + Control Mode (130)
            byte[] initCmd = new byte[]{(byte) 128, (byte) 130};
            serialPort.writeBytes(initCmd);
            logCommand("ROI Conection", initCmd);
            
            btnConnect.setEnabled(false);
            btnOpenFile.setEnabled(true);
            statusLabel.setText("Roomba Ready!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "SerialPort ERROR: " + ex.getMessage());
            logText("ERROR: " + ex.getMessage());
        }
    }

    private void chooseAndPlayMidi() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            new Thread(() -> streamMidi(fileChooser.getSelectedFile())).start();
        }
    }

    private void streamMidi(File midiFile) {
        try {
            logText("MidiLoad: " + midiFile.getName());
            Sequence sequence = MidiSystem.getSequence(midiFile);
            Sequencer sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.setSequence(sequence);

            sequencer.getTransmitter().setReceiver(new Receiver() {
                @Override
                public void send(MidiMessage message, long timeStamp) {
                    if (message instanceof ShortMessage) {
                        ShortMessage sm = (ShortMessage) message;
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            int note = sm.getData1();
                            sendNoteToRoomba(note);
                        }
                    }
                }
                @Override public void close() {}
            });
            SwingUtilities.invokeLater(() -> statusLabel.setText("Играю: " + midiFile.getName()));
            sequencer.start();
            
            while(sequencer.isRunning()) Thread.sleep(1000);
            
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Готово.");
                logText("Воспроизведение завершено.");
            });
            
        } catch (Exception ex) {
            ex.printStackTrace();
            logText("Ошибка MIDI: " + ex.getMessage());
        }
    }

    private void sendNoteToRoomba(int note) {
        try {
            byte[] songData = { (byte) 140, 0, 1, (byte) note, 8 };
            serialPort.writeBytes(songData);
            logCommand("Нота " + note + " (Запись 140)", songData);
            byte[] playCmd = new byte[]{(byte) 141, 0};
            serialPort.writeBytes(playCmd);
            logCommand("Нота " + note + " (Старт 141)", playCmd);
            
        } catch (SerialPortException e) {
            e.printStackTrace();
            logText("Ошибка отправки ROI: " + e.getMessage());
        }
    }
    private void logText(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    private void logCommand(String description, byte[] bytes) {
        StringJoiner hexString = new StringJoiner(" ");
        StringJoiner decString = new StringJoiner(", ");
        
        for (byte b : bytes) {
            int unsignedByte = b & 0xFF;
            hexString.add(String.format("%02X", unsignedByte));
            decString.add(String.valueOf(unsignedByte));
        }
        
        String time = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
        String logLine = String.format("[%s] %s | DEC: [%s] | HEX: [%s]\n", 
                time, description, decString.toString(), hexString.toString());
        
        SwingUtilities.invokeLater(() -> {
            logArea.append(logLine);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RoombaMusic().setVisible(true));
    }
}
