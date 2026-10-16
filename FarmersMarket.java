import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

// ===== Enum =====
enum ProduceCategory { VEGETABLE, FRUIT, DAIRY, GRAIN }

// ===== Custom exception (Exception Handling) =====
class InvalidDataException extends Exception {
    public InvalidDataException(String msg) { super(msg); }
}

// ===== Interface =====
interface Sellable {
    double calculateTotal(int qty);
    void sell(int qty) throws InvalidDataException;
}

// ===== Classes & Objects (with Constructors) =====
class Vendor {
    String id, name, phone;
    Vendor(String id, String name, String phone) { this.id = id; this.name = name; this.phone = phone; }
}

class Stall {
    int number; String vendorId;
    Stall(int number, String vendorId) { this.number = number; this.vendorId = vendorId; }
}

class Produce implements Sellable {
    int id; String name; ProduceCategory category; double price; int stock; String vendorId;

    Produce(int id, String name, ProduceCategory category, double price, int stock, String vendorId) {
        this.id = id; this.name = name; this.category = category;
        this.price = price; this.stock = stock; this.vendorId = vendorId;
    }
    public double calculateTotal(int qty) { return qty * price; }
    public void sell(int qty) throws InvalidDataException {
        if (qty <= 0) throw new InvalidDataException("Quantity must be greater than 0.");
        if (qty > stock) throw new InvalidDataException("Insufficient stock. Available: " + stock);
        stock -= qty;
    }
}

class Sale {
    int id, qty; String produceName, vendorId, time; double total;
    Sale(int id, String produceName, String vendorId, int qty, double total) {
        this.id = id; this.produceName = produceName; this.vendorId = vendorId;
        this.qty = qty; this.total = total;
        this.time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

// ===== Business logic: CRUD, Search, Sort, Validation =====
class MarketManager {
    static final int LOW_STOCK = 10, MAX_STALLS = 50;
    HashMap<String, Vendor> vendors = new HashMap<>();      // Vendor ID -> Vendor
    ArrayList<Produce> produceList = new ArrayList<>();     // Produce store
    HashMap<Integer, Stall> stalls = new HashMap<>();       // Stall no -> Stall
    LinkedList<Sale> sales = new LinkedList<>();            // Sales history
    int nextProduceId = 1, nextSaleId = 1;

    // ---- Vendor CRUD ----
    void addVendor(String id, String name, String phone) throws InvalidDataException {
        if (id.isEmpty() || name.isEmpty()) throw new InvalidDataException("Vendor ID and name are required.");
        if (!phone.matches("\\d{10}")) throw new InvalidDataException("Phone must be exactly 10 digits.");
        if (vendors.containsKey(id)) throw new InvalidDataException("Vendor ID already exists.");
        vendors.put(id, new Vendor(id, name, phone));
    }
    void updateVendor(String id, String name, String phone) throws InvalidDataException {
        Vendor v = vendors.get(id);
        if (v == null) throw new InvalidDataException("Vendor not found.");
        if (name.isEmpty()) throw new InvalidDataException("Name is required.");
        if (!phone.matches("\\d{10}")) throw new InvalidDataException("Phone must be exactly 10 digits.");
        v.name = name; v.phone = phone;
    }
    void deleteVendor(String id) throws InvalidDataException {
        if (vendors.remove(id) == null) throw new InvalidDataException("Vendor not found.");
        stalls.values().removeIf(s -> s.vendorId.equals(id));
        produceList.removeIf(p -> p.vendorId.equals(id));
    }

    // ---- Stall allocation ----
    void allocateStall(String vendorId, int number) throws InvalidDataException {
        if (!vendors.containsKey(vendorId)) throw new InvalidDataException("Vendor not found.");
        if (number < 1 || number > MAX_STALLS) throw new InvalidDataException("Stall number must be 1-" + MAX_STALLS + ".");
        if (stalls.containsKey(number)) throw new InvalidDataException("Stall " + number + " is already allocated.");
        stalls.values().removeIf(s -> s.vendorId.equals(vendorId)); // one stall per vendor
        stalls.put(number, new Stall(number, vendorId));
    }
    void releaseStall(int number) throws InvalidDataException {
        if (stalls.remove(number) == null) throw new InvalidDataException("Stall is not allocated.");
    }
    String stallOf(String vendorId) {
        for (Stall s : stalls.values()) if (s.vendorId.equals(vendorId)) return String.valueOf(s.number);
        return "-";
    }

    // ---- Produce CRUD ----
    void validateProduce(String name, double price, int stock, String vendorId) throws InvalidDataException {
        if (name.isEmpty()) throw new InvalidDataException("Produce name is required.");
        if (price <= 0) throw new InvalidDataException("Price must be greater than 0.");
        if (stock < 0) throw new InvalidDataException("Stock cannot be negative.");
        if (!vendors.containsKey(vendorId)) throw new InvalidDataException("Vendor ID not registered.");
    }
    void addProduce(String name, ProduceCategory c, double price, int stock, String vendorId) throws InvalidDataException {
        validateProduce(name, price, stock, vendorId);
        produceList.add(new Produce(nextProduceId++, name, c, price, stock, vendorId));
    }
    void updateProduce(int id, String name, ProduceCategory c, double price, int stock, String vendorId) throws InvalidDataException {
        validateProduce(name, price, stock, vendorId);
        Produce p = findProduce(id);
        p.name = name; p.category = c; p.price = price; p.stock = stock; p.vendorId = vendorId;
    }
    void deleteProduce(int id) throws InvalidDataException { produceList.remove(findProduce(id)); }
    Produce findProduce(int id) throws InvalidDataException {
        for (Produce p : produceList) if (p.id == id) return p;
        throw new InvalidDataException("Produce ID " + id + " not found.");
    }

    // ---- Sales ----
    Sale recordSale(int produceId, int qty) throws InvalidDataException {
        Produce p = findProduce(produceId);
        p.sell(qty);
        Sale s = new Sale(nextSaleId++, p.name, p.vendorId, qty, p.calculateTotal(qty));
        sales.addFirst(s);
        return s;
    }

    // ---- TreeMap: produce sorted by price ----
    TreeMap<Double, List<Produce>> byPrice() {
        TreeMap<Double, List<Produce>> map = new TreeMap<>();
        for (Produce p : produceList) map.computeIfAbsent(p.price, k -> new ArrayList<>()).add(p);
        return map;
    }

    // ---- Searching ----
    List<Produce> searchProduce(String q) {
        q = q.toLowerCase();
        List<Produce> res = new ArrayList<>();
        for (Produce p : produceList) {
            Vendor v = vendors.get(p.vendorId);
            String vName = v == null ? "" : v.name.toLowerCase();
            if (p.name.toLowerCase().contains(q) || p.vendorId.toLowerCase().contains(q) || vName.contains(q)
                    || p.category.name().toLowerCase().contains(q)) res.add(p);
        }
        return res;
    }
    List<Vendor> searchVendors(String q) {
        q = q.toLowerCase();
        List<Vendor> res = new ArrayList<>();
        for (Vendor v : vendors.values())
            if (v.id.toLowerCase().contains(q) || v.name.toLowerCase().contains(q)) res.add(v);
        res.sort(Comparator.comparing(v -> v.id));
        return res;
    }

    // ---- Sorting ----
    List<Produce> sorted(List<Produce> list, String mode) {
        List<Produce> r = new ArrayList<>(list);
        if (mode.startsWith("Price")) r.sort(Comparator.comparingDouble((Produce p) -> p.price));
        else if (mode.startsWith("Stock")) r.sort(Comparator.comparingInt((Produce p) -> p.stock));
        return r;
    }

    List<Produce> lowStock() {
        List<Produce> r = new ArrayList<>();
        for (Produce p : produceList) if (p.stock < LOW_STOCK) r.add(p);
        return r;
    }

    String report() {
        double revenue = 0; int items = 0;
        Map<String, Double> perVendor = new TreeMap<>();
        for (Sale s : sales) {
            revenue += s.total; items += s.qty;
            perVendor.merge(s.vendorId, s.total, Double::sum);
        }
        StringBuilder sb = new StringBuilder("===== MARKET REPORT =====\n");
        sb.append("Vendors registered : ").append(vendors.size()).append('\n');
        sb.append("Stalls allocated   : ").append(stalls.size()).append(" / ").append(MAX_STALLS).append('\n');
        sb.append("Produce items      : ").append(produceList.size()).append('\n');
        sb.append("Low-stock items    : ").append(lowStock().size()).append('\n');
        sb.append("Transactions       : ").append(sales.size()).append('\n');
        sb.append("Units sold         : ").append(items).append('\n');
        sb.append(String.format("Total revenue      : Rs. %.2f%n%n", revenue));
        sb.append("Revenue by vendor:\n");
        if (perVendor.isEmpty()) sb.append("  (no sales yet)\n");
        for (Map.Entry<String, Double> e : perVendor.entrySet()) {
            Vendor v = vendors.get(e.getKey());
            sb.append(String.format("  %s (%s): Rs. %.2f%n", e.getKey(), v == null ? "removed" : v.name, e.getValue()));
        }
        return sb.toString();
    }
}

// ===== Swing GUI =====
public class FarmersMarket extends JFrame {
    interface Action { void run() throws Exception; }

    MarketManager m = new MarketManager();
    DefaultTableModel vendorModel, stallModel, produceModel, saleModel, lowModel;
    JTextArea reportArea = new JTextArea();
    JTextField vId, vName, vPhone, vSearch, stVendor, stNum, pName, pPrice, pStock, pVendor, pSearch, sId, sQty;
    JComboBox<ProduceCategory> pCat = new JComboBox<>(ProduceCategory.values());
    JComboBox<String> pSort = new JComboBox<>(new String[]{"No sorting", "Price (low-high)", "Stock (low-high)"});
    JTable vendorTable, stallTable, produceTable;

    public FarmersMarket() {
        super("Farmers Market Vendor & Produce Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 560);
        setLocationRelativeTo(null);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Vendors", vendorPanel());
        tabs.addTab("Stalls", stallPanel());
        tabs.addTab("Produce", producePanel());
        tabs.addTab("Sales", salesPanel());
        tabs.addTab("Low Stock", lowStockPanel());
        tabs.addTab("Reports", reportPanel());
        tabs.addChangeListener(e -> refreshAll());
        add(tabs);
        seedData();
        refreshAll();
    }

    // ---- helpers ----
    DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; } };
    }
    JTextField field(JPanel p, String label, int w) {
        p.add(new JLabel(label)); JTextField f = new JTextField(w); p.add(f); return f;
    }
    JButton button(JPanel p, String text, Action a) {
        JButton b = new JButton(text);
        b.addActionListener(e -> {
            try { a.run(); refreshAll(); }
            catch (NumberFormatException ex) { error("Please enter valid numeric values."); }
            catch (Exception ex) { error(ex.getMessage()); }
        });
        p.add(b); return b;
    }
    void error(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }
    void info(String msg) { JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE); }
    JPanel layout(JPanel top, JTable t) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH); p.add(new JScrollPane(t), BorderLayout.CENTER); return p;
    }
    String fmt(double d) { return String.format("%.2f", d); }

    // ---- Vendor tab ----
    JPanel vendorPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        vId = field(top, "ID:", 6); vName = field(top, "Name:", 10); vPhone = field(top, "Phone:", 9);
        button(top, "Add", () -> { m.addVendor(vId.getText().trim(), vName.getText().trim(), vPhone.getText().trim()); clearVendor(); });
        button(top, "Update", () -> m.updateVendor(vId.getText().trim(), vName.getText().trim(), vPhone.getText().trim()));
        button(top, "Delete", () -> { m.deleteVendor(vId.getText().trim()); clearVendor(); });
        vSearch = field(top, "Search:", 8);
        button(top, "Go", () -> {});
        vendorModel = model("Vendor ID", "Name", "Phone", "Stall");
        vendorTable = new JTable(vendorModel);
        vendorTable.getSelectionModel().addListSelectionListener(e -> {
            int r = vendorTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                vId.setText(vendorModel.getValueAt(r, 0).toString());
                vName.setText(vendorModel.getValueAt(r, 1).toString());
                vPhone.setText(vendorModel.getValueAt(r, 2).toString());
            }
        });
        return layout(top, vendorTable);
    }
    void clearVendor() { vId.setText(""); vName.setText(""); vPhone.setText(""); }

    // ---- Stall tab ----
    JPanel stallPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stVendor = field(top, "Vendor ID:", 6); stNum = field(top, "Stall No (1-50):", 4);
        button(top, "Allocate", () -> m.allocateStall(stVendor.getText().trim(), Integer.parseInt(stNum.getText().trim())));
        button(top, "Release", () -> m.releaseStall(Integer.parseInt(stNum.getText().trim())));
        stallModel = model("Stall No", "Vendor ID", "Vendor Name");
        stallTable = new JTable(stallModel);
        return layout(top, stallTable);
    }

    // ---- Produce tab ----
    JPanel producePanel() {
        JPanel top = new JPanel(new GridLayout(2, 1));
        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT)), r2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pName = field(r1, "Name:", 8); r1.add(new JLabel("Category:")); r1.add(pCat);
        pPrice = field(r1, "Price:", 5); pStock = field(r1, "Stock:", 4); pVendor = field(r1, "Vendor ID:", 5);
        button(r1, "Add", () -> m.addProduce(pName.getText().trim(), (ProduceCategory) pCat.getSelectedItem(),
                Double.parseDouble(pPrice.getText().trim()), Integer.parseInt(pStock.getText().trim()), pVendor.getText().trim()));
        button(r1, "Update", () -> m.updateProduce(selectedProduceId(), pName.getText().trim(), (ProduceCategory) pCat.getSelectedItem(),
                Double.parseDouble(pPrice.getText().trim()), Integer.parseInt(pStock.getText().trim()), pVendor.getText().trim()));
        button(r1, "Delete", () -> m.deleteProduce(selectedProduceId()));
        pSearch = field(r2, "Search (name/vendor/category):", 12);
        r2.add(new JLabel("Sort:")); r2.add(pSort);
        button(r2, "Apply", () -> {});
        button(r2, "TreeMap view (by price)", () -> {
            StringBuilder sb = new StringBuilder("Produce sorted by price (TreeMap):\n\n");
            for (Map.Entry<Double, List<Produce>> e : m.byPrice().entrySet())
                for (Produce p : e.getValue()) sb.append(String.format("Rs. %.2f  -  %s (%s)%n", e.getKey(), p.name, p.category));
            info(sb.toString());
        });
        top.add(r1); top.add(r2);
        produceModel = model("ID", "Name", "Category", "Price", "Stock", "Vendor ID");
        produceTable = new JTable(produceModel);
        produceTable.getSelectionModel().addListSelectionListener(e -> {
            int r = produceTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                pName.setText(produceModel.getValueAt(r, 1).toString());
                pCat.setSelectedItem(ProduceCategory.valueOf(produceModel.getValueAt(r, 2).toString()));
                pPrice.setText(produceModel.getValueAt(r, 3).toString());
                pStock.setText(produceModel.getValueAt(r, 4).toString());
                pVendor.setText(produceModel.getValueAt(r, 5).toString());
            }
        });
        return layout(top, produceTable);
    }
    int selectedProduceId() throws InvalidDataException {
        int r = produceTable.getSelectedRow();
        if (r < 0) throw new InvalidDataException("Select a produce row first.");
        return (int) produceModel.getValueAt(r, 0);
    }

    // ---- Sales tab ----
    JPanel salesPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sId = field(top, "Produce ID:", 5); sQty = field(top, "Quantity:", 5);
        button(top, "Record Sale", () -> {
            Sale s = m.recordSale(Integer.parseInt(sId.getText().trim()), Integer.parseInt(sQty.getText().trim()));
            info("Sale recorded. Total: Rs. " + fmt(s.total));
            sQty.setText("");
        });
        saleModel = model("Sale ID", "Produce", "Vendor ID", "Qty", "Total (Rs.)", "Time");
        return layout(top, new JTable(saleModel));
    }

    // ---- Low stock tab ----
    JPanel lowStockPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Produce with stock below " + MarketManager.LOW_STOCK + " units:"));
        lowModel = model("ID", "Name", "Category", "Stock", "Vendor ID");
        return layout(top, new JTable(lowModel));
    }

    // ---- Report tab ----
    JPanel reportPanel() {
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(reportArea), BorderLayout.CENTER);
        return p;
    }

    // ---- Refresh ----
    void refreshAll() {
        if (vendorModel == null || produceModel == null || lowModel == null) return;
        vendorModel.setRowCount(0);
        for (Vendor v : m.searchVendors(vSearch.getText().trim()))
            vendorModel.addRow(new Object[]{v.id, v.name, v.phone, m.stallOf(v.id)});

        stallModel.setRowCount(0);
        new TreeMap<>(m.stalls).forEach((n, s) -> {
            Vendor v = m.vendors.get(s.vendorId);
            stallModel.addRow(new Object[]{n, s.vendorId, v == null ? "" : v.name});
        });

        produceModel.setRowCount(0);
        for (Produce p : m.sorted(m.searchProduce(pSearch.getText().trim()), (String) pSort.getSelectedItem()))
            produceModel.addRow(new Object[]{p.id, p.name, p.category, fmt(p.price), p.stock, p.vendorId});

        saleModel.setRowCount(0);
        for (Sale s : m.sales)
            saleModel.addRow(new Object[]{s.id, s.produceName, s.vendorId, s.qty, fmt(s.total), s.time});

        lowModel.setRowCount(0);
        for (Produce p : m.lowStock())
            lowModel.addRow(new Object[]{p.id, p.name, p.category, p.stock, p.vendorId});

        reportArea.setText(m.report());
    }

    // ---- Sample data so the app isn't empty ----
    void seedData() {
        try {
            m.addVendor("V01", "Ramesh Patil", "9876543210");
            m.addVendor("V02", "Sunita Farms", "9123456780");
            m.allocateStall("V01", 1); m.allocateStall("V02", 2);
            m.addProduce("Tomato", ProduceCategory.VEGETABLE, 40, 50, "V01");
            m.addProduce("Mango", ProduceCategory.FRUIT, 120, 8, "V01");
            m.addProduce("Milk", ProduceCategory.DAIRY, 60, 30, "V02");
            m.addProduce("Wheat", ProduceCategory.GRAIN, 35, 100, "V02");
        } catch (InvalidDataException e) { error(e.getMessage()); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FarmersMarket().setVisible(true));
    }
}
