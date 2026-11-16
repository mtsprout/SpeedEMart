import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import java.util.function.DoubleUnaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * SPEED-E-MART NIGHT SHIFT -- a cartoon convenience-store simulator built to raise your blood pressure.
 * Pure Java 21 / Swing. No dependencies.
 */
public class Game extends JPanel implements ActionListener, KeyListener, MouseListener {
    static final int W = 1280, H = 720;
    static final double SHIFT = 480;               // seconds of real time for the 8-hour shift
    static final int[] DEN = {1, 5, 10, 20};
    static final int[] FLOAT = {8, 5, 3, 3};       // what stays in the drawer after a drop
    static final int[] TARGET = {20, 10, 6, 6};    // fully stocked drawer
    static final double REG_X = 585, REG_Y = 252;
    static final double CLIMB_X = 585, CLIMB_Y = 150;
    static final double DOOR_X = 585, DOOR_Y = 735;

    // ---------- the shift schedule (1 real second = 1 game minute; 0 = 10:00 PM) ----------
    static class Phase { int from; String name; double gap; String msg;
        Phase(int f, String n, double g, String m) { from = f; name = n; gap = g; msg = m; } }
    static final Phase[] PHASES = {
        new Phase(0,   "EVENING TRAFFIC", 10, "Evening traffic. Steady customers."),
        new Phase(55,  "QUIET HOURS",     30, "Quiet now. Time for chores!"),
        new Phase(150, "LATE-NIGHT LULL", 26, "Late-night lull. Keep the store tidy."),
        new Phase(210, "BEER RUSH",       18, "1:30 AM - the bars just let out. BEER RUSH!"),
        new Phase(250, "QUIET HOURS",     28, "Rush is over. Catch your breath and clean up."),
        new Phase(300, "LOTTERY FEVER",   20, "3 AM - somebody heard the jackpot is huge..."),
        new Phase(345, "DEAD OF NIGHT",   30, "Dead of night. Do your chores."),
        new Phase(390, "MORNING COMMUTE", 12, "Early commuters want cigs and coffee."),
        new Phase(450, "FINAL STRETCH",    9, "Almost 6 AM. Last push!"),
    };
    static class Event { int min, type, count; String msg;
        Event(int m, int t, int c, String s) { min = m; type = t; count = c; msg = s; } }
    static final Event[] EVENTS = {
        new Event(8,   -1,        2, "First customers are coming in."),
        new Event(210, 5 /*beer*/,    5, "BEER RUSH! Everyone needs an ID check."),
        new Event(300, 3 /*scratch*/, 2, "Scratcher guys are here..."),
        new Event(390, 2 /*cigs*/,    3, "Commuters want TOP-shelf cigs."),
        new Event(455, -1,        4, "Final rush!"),
    };

    enum Scene { TITLE, PLAY, OVER, WIN }
    static final int T_NORMAL = 0, T_BIG = 1, T_CIG = 2, T_SCRATCH = 3, T_ASK = 4, T_BEER = 5, T_ROB = 6;
    static final int N_RING = 0, N_PACK = 1, N_CARD = 2, N_PAY = 3, N_SCRATCH = 4, N_ASK = 5, N_ID = 6;
    static final int S_SHOP = 0, S_BROWSE = 1, S_QUEUE = 2, S_LEAVE = 3, S_ASKGO = 4, S_ASK = 5, S_ROBGO = 6, S_ROBTAKE = 7;
    static final int C_NONE = 0, C_BOX = 2, C_PACK = 3;

    // ---------- fonts / palette ----------
    Font fBig = new Font("Arial Rounded MT Bold", Font.BOLD, 46);
    Font fMid = new Font("Arial Rounded MT Bold", Font.BOLD, 22);
    Font fSm = new Font("Arial Rounded MT Bold", Font.BOLD, 14);
    Font fXs = new Font("Arial Rounded MT Bold", Font.BOLD, 12);
    Font fTag = new Font("Arial Rounded MT Bold", Font.BOLD, 12);
    Font fHint = new Font("Arial Rounded MT Bold", Font.BOLD, 18);
    static final Color OUT = new Color(30, 30, 45);
    static final Color[] SHIRTS = {new Color(235, 80, 80), new Color(70, 140, 230), new Color(90, 200, 110),
            new Color(250, 170, 50), new Color(170, 100, 220), new Color(60, 200, 200), new Color(240, 120, 180)};
    static final Color[] SKINS = {new Color(255, 214, 160), new Color(235, 180, 130), new Color(190, 130, 90),
            new Color(130, 85, 60), new Color(255, 224, 189)};
    static final String[] NAMES = {"Dave", "Brenda", "Chad", "Linda", "Trent", "Gus", "Karen", "Doug", "Marge",
            "Kyle", "Barb", "Todd", "Sheryl", "Randy", "Pam", "Gary"};

    // ---------- classes ----------
    static class Spill { double x, y, hp = 100; }
    static class Shelf {
        Rectangle2D.Double r; String name; int stock = 8; double face = 100; boolean faced = true;
        Shelf(double x, double y, double w, String n) { r = new Rectangle2D.Double(x, y, w, 34); name = n; }
        double standX() { return r.getCenterX(); }
        double standY() { return r.getMaxY() + 26; }
    }
    static class FT { double x, y, life = 1.6; String s; Color c; }
    class Task {
        String id, label; int prio; double x, y; boolean hold;
        DoubleConsumer work; DoubleSupplier ui; Runnable tap, undo;
    }
    class Customer {
        int type, state, need, total, bill, rounds, round, tickets, shelf;
        String name, say = "", q = "", wants = ""; double idDur = 1.0; double sayT;
        Color shirt, skin; double x, y, dir = 1, pat = 1, patMax = 40, browse, scratchT, bob;
        double calm; int shouts; boolean sodaUser, sodaHappy, paused, seen, card, atSlot, yelled, flip, flipped, answered, angry, gone;
        double[] pr = new double[1];
        List<double[]> path = new ArrayList<>();
    }

    // ---------- game state ----------
    Random rnd = new Random();
    Scene scene = Scene.TITLE;
    boolean up, down, left, right, sprint, eDown, ePressed, qPressed;
    int mouseX, mouseY;
    double px, py, stun, shake, toastT, t, spawnT, spillT, healthShown;
    double rage = 0, snapT = 0, trickleT = 10, burstT = 0; int burstLeft = 0, burstForce = -1, nSpawned = 0, phaseIdx = -1; boolean snapWarned;
    boolean[] evFired = new boolean[EVENTS.length], evWarned = new boolean[EVENTS.length];
    String phaseName = "";
    static final double AWAY_DRAIN = 1.5;   // extra rage drained per second while you are away from the register
    static final double CW_RAGE_MULT = 0.8;   // while the co-worker is in the store, all rage gains are 20% lower, on every night
    static final double SNAP_LIMIT = 7.0;   // seconds of sustained high rage before a meltdown
    double rush = 0, swishT, reassureCd = 0; boolean fPressed;
    double sodaLeakT = 12; boolean sodaLeakWarned;
    boolean packEarly;   // true if the pack in your hands was grabbed before its customer reached the front
    // full-screen mop-beating cinematic
    boolean cineOn; double cineT, cineEnd, hitFlash, hitX, hitY, focusX, focusY; String hitWord = "WHACK!", cineCaption = "";
    double beatT = 0, beatSwing = 0; int beatHits = 0; Customer beatTarget;
    static final double ROB_LIMIT = 25.0;   // seconds over $350 before a robber shows up (night 1)
    int night = 1;                           // each shift survived makes the next one harder
    /** Difficulty multiplier: +10% per night survived. */
    double nm() { return 1 + 0.10 * (night - 1); }
    double robLimit() { return Math.max(12, ROB_LIMIT - 2 * (night - 1)); }
    int runScore, lastShiftScore;   // running total over the shifts you survived, and the shift that just ended
    void startNext() {
        if (scene == Scene.TITLE) { night = 1; runScore = 0; }
        else if (scene == Scene.WIN) night++;     // survived: next night is harder. Fired: retry the same night.
        newGame();
    }
    double robT = 0, robCd = 0; int robbedTotal = 0, robberies = 0;          // rush: 0..1, rises as you succeed, falls when customers storm out
    boolean help = false;
    int carry; boolean climbing; String toast = "";
    double health;
    int tips, sales, happy, angryCt, combo, bestCombo, served;
    int[] drawer = new int[4], safe = new int[4], given = new int[4];
    int safeBig, snaps;
    int[] big = new int[2], bigGiven = new int[2];   // $50 and $100 bills customers paid with: usable as change until you DROP CASH
    static final int[] BIGDEN = {50, 100};
    int bigPile() { return big[0] * 50 + big[1] * 100; }
    void clearGiven() { Arrays.fill(given, 0); Arrays.fill(bigGiven, 0); }
    double soda, rugDirt;
    double[] climbPr = new double[1], safePr = new double[1];
    boolean drawerWarned;
    Task heldTask, activeTask;
    // co-worker: one call-in per shift; costs half your tips; does chores (not customers or cash) until closing
    boolean cwConfirm; Rectangle2D.Double[] cwBtn = new Rectangle2D.Double[2];   // "call co-worker?" dialog: Yes, No
    boolean cwLeaving; double cwLeft;   // minutes (= seconds of game time) they have left; they walk out when it runs out
    boolean cwUsed, cwOn; double cwX, cwY, cwDir = 1; boolean cwMoving, cwWorking; double cwStockT;
    /** How long a co-worker stays: 90 minutes on night 1, 60 on night 2, 30 from night 3 on. */
    int cwMinutes() { return Math.max(30, 120 - 30 * night); }
    /** How much of your current rage the call takes off: 50% on night 1, 40% on night 2, ... down to 0% from night 6 on. */
    int cwCalmPct() { return Math.max(0, 60 - 10 * night); }
    static final double CW_SPEED = 150, CW_RATE = 0.7;   // walking speed, and how fast they work compared with you
    List<Customer> customers = new ArrayList<>(), queue = new ArrayList<>();
    List<Spill> spills = new ArrayList<>();
    List<FT> fts = new ArrayList<>();
    List<Shelf> shelves = new ArrayList<>();
    List<Rectangle2D.Double> walls = new ArrayList<>();
    Rectangle2D.Double rugR = new Rectangle2D.Double(520, 650, 130, 38);
    Rectangle2D.Double sodaR = new Rectangle2D.Double(1085, 60, 115, 125);
    Point2D.Double boxPile = new Point2D.Double(235, 185), safeP = new Point2D.Double(95, 172);
    Rectangle2D.Double[] panelBtn = new Rectangle2D.Double[8]; // 4 denoms, clear, done, $50, $100
    Rectangle2D.Double[] endBtn = new Rectangle2D.Double[2];   // end-of-shift dialog: Okay, Quit
    long lastNs;
    javax.swing.Timer timer;

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--test")) { selfTest(a.length > 1 ? a[1] : "shot"); return; }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("SPEED-E-MART: Night Shift");
            Game g = new Game();
            f.setContentPane(g);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
            g.start();
        });
    }

    Game() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addFocusListener(new java.awt.event.FocusAdapter() {   // a key released while another window had focus would stay "held"
            public void focusLost(java.awt.event.FocusEvent e) { up = down = left = right = sprint = eDown = false; }
        });
        addMouseListener(this);
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
        });
        timer = new javax.swing.Timer(16, this);
        buildWorld();
    }

    void start() { lastNs = System.nanoTime(); timer.start(); }

    void buildWorld() {
        shelves.clear(); walls.clear();
        String[] n = {"Chips", "Candy", "Snacks", "Bread", "Cereal", "Drinks"};
        shelves.add(new Shelf(340, 420, 200, n[0]));
        shelves.add(new Shelf(340, 540, 200, n[1]));
        shelves.add(new Shelf(660, 420, 230, n[2]));
        shelves.add(new Shelf(660, 540, 230, n[3]));
        shelves.add(new Shelf(960, 330, 220, n[4]));
        shelves.add(new Shelf(960, 470, 220, n[5]));
        walls.add(new Rectangle2D.Double(470, 232, 230, 38));  // counter (short, so it's quick to get around)
        walls.add(new Rectangle2D.Double(340, 44, 420, 74));   // cig wall
        walls.add(new Rectangle2D.Double(40, 246, 286, 10));   // backroom bottom wall
        walls.add(new Rectangle2D.Double(316, 40, 10, 112));   // backroom right wall (upper)
        walls.add(new Rectangle2D.Double(316, 232, 10, 24));   // backroom right wall (lower)
        walls.add(sodaR);
        for (Shelf s : shelves) walls.add(s.r);
        walls.add(new Rectangle2D.Double(40, 20, 1200, 20));   // top
        walls.add(new Rectangle2D.Double(20, 20, 20, 680));
        walls.add(new Rectangle2D.Double(1240, 20, 20, 680));
        walls.add(new Rectangle2D.Double(20, 690, 500, 20));
        walls.add(new Rectangle2D.Double(650, 690, 610, 20));
    }

    void newGame() {
        scene = Scene.PLAY;
        t = 0; health = 62; healthShown = 62; tips = sales = happy = angryCt = combo = bestCombo = served = 0;
        px = 500; py = 170; carry = C_NONE; stun = 0; climbing = false;
        drawer = new int[]{20, 10, 6, 6};
        safe = new int[]{80, 40, 25, 12}; Arrays.fill(big, 0); safeBig = 0; clearGiven();
        soda = 20; rugDirt = 10;
        customers.clear(); queue.clear(); spills.clear(); fts.clear();
        for (Shelf s : shelves) { s.stock = 6 + rnd.nextInt(4); s.face = 80 + rnd.nextInt(20); }
        spawnT = 6; spillT = 40; toast = ""; drawerWarned = false; rush = Math.min(0.5, 0.06 * (night - 1)); help = false;
        robT = 0; robCd = 40; robbedTotal = 0; robberies = 0;
        cwConfirm = false; snaps = 0; cwUsed = cwOn = cwLeaving = false; cwMoving = cwWorking = false; cwStockT = 0;
        sodaLeakT = 12; sodaLeakWarned = false; cineOn = false; beatT = 0; beatTarget = null; hitFlash = 0; packEarly = false;
        rage = 0; snapT = 0; snapWarned = false; trickleT = 22; burstLeft = 0; burstForce = -1; burstT = 0; nSpawned = 0; phaseIdx = -1; phaseName = "";
        Arrays.fill(evFired, false); Arrays.fill(evWarned, false);
        toast(night > 1 ? "NIGHT " + night + ": customers are meaner, busier and less patient." : "Press H any time for help.  M mutes sound.");
        Snd.play("bell");
    }

    // =====================================================================
    //  MAIN LOOP
    // =====================================================================
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNs) / 1e9);
        lastNs = now;
        step(dt);
        repaint();
        Toolkit.getDefaultToolkit().sync();
    }

    void step(double dt) {
        healthShown += (health - healthShown) * Math.min(1, dt * 6);
        if (scene != Scene.PLAY) { ePressed = qPressed = false; return; }
        if (help || cwConfirm) { ePressed = qPressed = false; return; }
        double rdt = dt;                       // real time, for the cinematic itself
        if (cineOn) {
            cineT += rdt; hitFlash = Math.max(0, hitFlash - rdt);
            if (cineT >= cineEnd) cineOn = false;
            dt *= 0.3;                         // the rest of the world drops into slow motion
        }
        t += dt;
        if (t >= SHIFT) { scene = Scene.WIN; lastShiftScore = shiftScore(true); runScore += lastShiftScore; Snd.play("win"); return; }
        stun -= dt; shake -= dt; toastT -= dt;
        movePlayer(dt);
        beatLogic(rdt);
        handleTasks(dt);
        coworkerLogic(dt);
        reassureCd -= dt;
        if (fPressed) reassure();
        if (qPressed && carry != C_NONE) { dropCarry(); Snd.play("thud"); }
        spawnLogic(dt);
        worldEvents(dt);
        for (Customer c : new ArrayList<>(customers)) updateCustomer(c, dt);
        customers.removeIf(c -> c.gone);
        for (Iterator<FT> it = fts.iterator(); it.hasNext(); ) {
            FT f = it.next(); f.life -= dt; f.y -= 28 * dt; if (f.life <= 0) it.remove();
        }
        rageLogic(dt);
        robberyLogic(dt);
        healthLogic(dt);
        ePressed = qPressed = fPressed = false;
    }

    double reassureCooldown() { return Math.min(30, 15 + 2 * (night - 1)); }

    /** Shout 'be right with you!' -- calms everyone waiting for a few seconds. */
    void reassure() {
        if (stun > 0) return;
        if (reassureCd > 0) { toast("Your voice needs a rest... (" + (int) Math.ceil(reassureCd) + "s)"); return; }
        // The effect shrinks every night, and each customer only believes you so many times.
        double boost = Math.max(0.04, 0.12 - 0.02 * (night - 1));
        double freeze = Math.max(2.0, 4.0 - 0.5 * (night - 1));
        int helped = 0;
        for (Customer c : queue) {
            if (c.need == N_SCRATCH) continue;
            if (c.shouts >= 2) { say(c, "You said that already!", 2.2); continue; }
            double k = c.shouts == 0 ? 1.0 : 0.5;          // 1st time full strength, 2nd time half, then nothing
            c.pat = Math.min(1, c.pat + boost * k); c.calm = Math.max(c.calm, freeze * k); c.shouts++;
            say(c, c.shouts == 1 ? new String[]{"Okay, thanks.", "Alright, I'll wait.", "Fine. Take your time."}[rnd.nextInt(3)] : "Mmhm. Sure.", 2.2);
            helped++;
        }
        if (helped == 0) { toast(queue.isEmpty() ? "Nobody in line to calm down." : "They've heard that one already. Nobody's buying it."); return; }
        reassureCd = reassureCooldown(); Snd.play("click");
        ft(px, py - 80, "\"Be right with you!\"", new Color(130, 230, 170));
    }

    double lerp(double a, double b, double k) { return a + (b - a) * Math.max(0, Math.min(1, k)); }
    double dist(double a, double b, double c, double d) { return Math.hypot(a - c, b - d); }
    double prog() { return t / SHIFT; }

    // =====================================================================
    //  PLAYER
    // =====================================================================
    void movePlayer(double dt) {
        double dx = (right ? 1 : 0) - (left ? 1 : 0), dy = (down ? 1 : 0) - (up ? 1 : 0);
        if (stun > 0 || climbing) { dx = dy = 0; }
        double len = Math.hypot(dx, dy);
        if (len > 0) { dx /= len; dy /= len; if (dx != 0) facingDir = dx > 0 ? 1 : -1; }
        double sp = 185;
        if (sprint && carry == C_NONE) sp = 260;
        if (carry == C_BOX) sp = 140;
        if (heldTask != null) sp *= 0.7;
        moveAxis(dx * sp * dt, 0);
        moveAxis(0, dy * sp * dt);
    }
    double facingDir = 1;

    void moveAxis(double mx, double my) {
        // small sub-steps, so a long frame can never carry the player into (or through) a wall
        int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(mx), Math.abs(my)) / 5.0));
        for (int i = 0; i < n; i++) { px += mx / n; py += my / n; pushOutOfWalls(); }
        px = Math.max(60, Math.min(1220, px));
        py = Math.max(60, Math.min(675, py));
        pushOutOfWalls();
        px = Math.max(60, Math.min(1220, px));
        py = Math.max(60, Math.min(675, py));
    }

    /** Keeps the player's circle (radius 15) out of every wall; if the centre is somehow inside one, shoves it out the nearest side. */
    void pushOutOfWalls() {
        double r = 15;
        for (Rectangle2D.Double w : walls) {
            if (px >= w.x && px <= w.x + w.width && py >= w.y && py <= w.y + w.height) {
                // nearest side whose exit is still inside the play area (the screen-edge clamp would undo the others)
                double[][] ex = {{px - w.x, w.x - r, py}, {w.x + w.width - px, w.x + w.width + r, py},
                                 {py - w.y, px, w.y - r}, {w.y + w.height - py, px, w.y + w.height + r}};
                double[] pick = null;
                for (double[] e : ex) {
                    boolean ok = e[1] >= 60 && e[1] <= 1220 && e[2] >= 60 && e[2] <= 675;
                    if (ok && (pick == null || e[0] < pick[0])) pick = e;
                }
                if (pick == null) { pick = ex[0]; for (double[] e : ex) if (e[0] < pick[0]) pick = e; }
                px = pick[1]; py = pick[2];
                continue;
            }
            double cx = Math.max(w.x, Math.min(px, w.x + w.width));
            double cy = Math.max(w.y, Math.min(py, w.y + w.height));
            double d = dist(px, py, cx, cy);
            if (d < r) { px = cx + (px - cx) / d * r; py = cy + (py - cy) / d * r; }
        }
    }

    void dropCarry() {
        if (carry == C_BOX) { carry = C_NONE; ft(px, py - 60, "dropped box", Color.WHITE); }
        else if (carry == C_PACK) { carry = C_NONE; packEarly = false; ft(px, py - 60, "put pack back", Color.WHITE); }
    }

    // =====================================================================
    //  TASKS (everything you can hold/tap E on)
    // =====================================================================
    Customer front() {
        if (queue.isEmpty()) return null;
        Customer c = queue.get(0);
        return c.atSlot ? c : null;
    }

    Task holdDur(String id, String label, int prio, double x, double y, double dur, double[] pr, Runnable done, double undoAmt) {
        Task k = new Task();
        k.id = id; k.label = label; k.prio = prio; k.x = x; k.y = y; k.hold = true;
        k.work = dt -> { pr[0] += dt / dur; if (pr[0] >= 1) { pr[0] = 0; done.run(); } };
        k.ui = () -> pr[0];
        k.undo = () -> pr[0] = Math.max(0, pr[0] - undoAmt);
        return k;
    }
    Task tap(String id, String label, int prio, double x, double y, Runnable r) {
        Task k = new Task();
        k.id = id; k.label = label; k.prio = prio; k.x = x; k.y = y; k.tap = r;
        return k;
    }

    double rectDist(Rectangle2D r, double x, double y) {
        double cx = Math.max(r.getMinX(), Math.min(x, r.getMaxX()));
        double cy = Math.max(r.getMinY(), Math.min(y, r.getMaxY()));
        return dist(x, y, cx, cy);
    }

    /** A cigarette customer who still needs their pack (even if they're still shopping or waiting in line). */
    Customer cigWaiting() {
        for (Customer c : customers) if (c.type == T_CIG && c.need == N_PACK && !c.gone && c.state != S_LEAVE) return c;
        return null;
    }

    boolean atRegister() { return py < 265 && dist(px, py, REG_X, REG_Y) < 115; }

    List<Task> buildTasks() {
        List<Task> L = new ArrayList<>();
        Customer c0 = front();
        // --- askers follow you around
        for (Customer c : customers)
            if (c.type == T_ASK && c.state == S_ASK && !c.answered && dist(c.x, c.y, px, py) < 100)
                L.add(holdDur("ask" + c.hashCode(), "Answer: \"" + c.q + "\"", 9, c.x, c.y, 0.9, c.pr,
                        () -> { c.answered = true; finish(c, 1.0); }, 0.6));
        // --- register
        if (atRegister() && c0 != null) {
            if (c0.need == N_ID)
                L.add(holdDur("id", "Check their ID", 8, REG_X, REG_Y, c0.idDur, c0.pr,
                        () -> { c0.need = N_RING; say(c0, "There. Happy?", 2.5); }, 0.5));
            else if (c0.need == N_RING)
                L.add(holdDur("ring", c0.type == T_CIG ? "Ring up the cigarettes (slow...)" : "Ring up order", 8, REG_X, REG_Y, c0.type == T_CIG ? 1.6 : 0.55, c0.pr, () -> ringDone(c0), 0.5));
            else if (c0.need == N_PACK && carry == C_PACK)
                L.add(tap("hand", packEarly ? "Hand over the pack (it's ready!)" : "Hand over the pack", 8, REG_X, REG_Y, () -> {
                    carry = C_NONE; c0.need = N_RING;
                    if (packEarly) {   // proactive catch bonus
                        packEarly = false;
                        if (c0.flip && rnd.nextInt(100) < 67) c0.flip = false;   // being proactive: ~45% wrong-pack odds drop to ~15%
                        tips += 5; health = Math.min(100, health + 3); calmRage(8); bumpRush(0.03);
                        c0.pat = Math.min(1, c0.pat + 0.25);
                        say(c0, new String[]{"Wow, you already had it!", "Oh nice, that was fast.", "You read my mind!"}[rnd.nextInt(3)], 3);
                        ft(px, py - 90, "PROACTIVE!  +$5", new Color(255, 225, 60));
                        Snd.play("kaching");
                    } else { c0.say = "Finally."; c0.sayT = 2.5; }
                }));
        }
        // --- climb up to the top-shelf cigarettes (slow!), standing under the cig wall at the dashed ring
        // ...and you can do it EARLY: as soon as a cigarette customer is in the store, get ahead of them.
        boolean frontWantsPack = c0 != null && c0.need == N_PACK;
        if ((frontWantsPack || cigWaiting() != null) && carry == C_NONE && dist(px, py, CLIMB_X, CLIMB_Y) < 70) {
            L.add(holdDur("climb", frontWantsPack ? "Climb up for the cigs" : "Climb for the cigs NOW (get ahead of them)", 7, CLIMB_X, CLIMB_Y, 4.0, climbPr, () -> {
                packEarly = !(front() != null && front().need == N_PACK);   // did you get ahead of them?
                carry = C_PACK; climbing = false; ft(px, py - 70, packEarly ? "got the pack EARLY!" : "got the pack!", Color.WHITE); }, 0.6));
        }
        // --- safe
        if (dist(px, py, safeP.x, safeP.y) < 85) {
            if (drawerExcess() > 0)
                L.add(holdDur("drop", "DROP CASH: put $" + drawerExcess() + " in the safe", 6, safeP.x, safeP.y, 1.1, safePr, this::dropCash, 0.5));
            boolean can = false;
            for (int i = 0; i < 4; i++) if (drawer[i] < TARGET[i] && safe[i] > 0) can = true;
            if (can)
                L.add(holdDur("chg", "BUY CASH: restock small bills from the safe", 5, safeP.x, safeP.y, 1.1, safePr, this::grabChange, 0.5));
        }
        // --- boxes / stocking
        if (carry == C_NONE && dist(px, py, boxPile.x, boxPile.y) < 70)
            L.add(tap("box", "Grab a case of stock", 6, boxPile.x, boxPile.y, () -> carry = C_BOX));
        for (Shelf s : shelves) {
            double d = rectDist(s.r, px, py);
            if (d < 48) {
                if (carry == C_BOX && s.stock < 10)
                    L.add(tap("stock" + s.name, "Stock " + s.name, 5, s.standX(), s.r.getCenterY(), () -> {
                        s.stock = 10; carry = C_NONE; calmRage(5); health = Math.min(100, health + 4); ft(px, py - 60, "+4 health", Color.GREEN); }));
                if (s.face < 92) {
                    Task k = new Task();
                    k.id = "face" + s.name; k.label = "Front-face " + s.name; k.prio = 2; k.hold = true;
                    k.x = s.standX(); k.y = s.r.getCenterY();
                    k.work = dt -> {
                        double add = dt * 32; s.face = Math.min(100, s.face + add); health = Math.min(100, health + add * 0.03);
                        if (s.face >= 100 && !s.faced) { s.faced = true; ft(px, py - 60, "tidy! +2", Color.GREEN); health = Math.min(100, health + 2); }
                    };
                    k.ui = () -> s.face / 100;
                    L.add(k);
                }
            }
        }
        // --- mop spills
        for (Spill sp : spills)
            if (dist(px, py, sp.x, sp.y) < 55) {
                Task k = new Task();
                k.id = "spill" + sp.hashCode(); k.label = "Mop the spill"; k.prio = 4; k.hold = true; k.x = sp.x; k.y = sp.y;
                k.work = dt -> { sp.hp -= dt * 42; if (sp.hp <= 0) { spills.remove(sp); health = Math.min(100, health + 5); calmRage(6); ft(px, py - 60, "sparkly! +5", Color.GREEN); } };
                k.ui = () -> 1 - sp.hp / 100;
                k.undo = () -> sp.hp = Math.min(100, sp.hp + 25);
                L.add(k);
                break;
            }
        // --- soda machine
        if (soda > 12 && rectDist(sodaR, px, py) < 55) {
            Task k = new Task();
            k.id = "soda"; k.label = "Clean the soda machine"; k.prio = 3; k.hold = true; k.x = sodaR.getCenterX(); k.y = sodaR.getMaxY();
            k.work = dt -> { double d = Math.min(soda, dt * 18); soda -= d; health = Math.min(100, health + d * 0.08); };
            k.ui = () -> 1 - soda / 100;
            k.undo = () -> soda = Math.min(100, soda + 8);
            L.add(k);
        }
        // --- rug
        if (rugDirt > 12 && rectDist(rugR, px, py) < 50) {
            Task k = new Task();
            k.id = "rug"; k.label = "Shake out the rug"; k.prio = 3; k.hold = true; k.x = rugR.getCenterX(); k.y = rugR.getCenterY();
            k.work = dt -> { double d = Math.min(rugDirt, dt * 26); rugDirt -= d; health = Math.min(100, health + d * 0.06); };
            k.ui = () -> 1 - rugDirt / 100;
            k.undo = () -> rugDirt = Math.min(100, rugDirt + 8);
            L.add(k);
        }
        return L;
    }

    void handleTasks(double dt) {
        heldTask = null;
        List<Task> L = buildTasks();
        Task best = null; double bd = 1e9;
        for (Task k : L) {
            double d = dist(px, py, k.x, k.y);
            if (best == null || k.prio > best.prio || (k.prio == best.prio && d < bd)) { best = k; bd = d; }
        }
        activeTask = best;
        if (best == null) { climbing = false; return; }
        if (best.id.equals("climb") && eDown && stun <= 0) climbing = true; else climbing = false;
        if (stun > 0) return;
        if (best.hold) {
            if (eDown) {
                best.work.accept(dt); heldTask = best;
                swishT -= dt;
                if (swishT <= 0) { Snd.play(best.id.equals("climb") ? "creak" : "swish"); swishT = 0.32; }
            }
        } else if (ePressed) { best.tap.run(); Snd.play("pick"); }
    }

    void interrupt(String who, String msg) {
        if (heldTask != null) {
            if (heldTask.undo != null) heldTask.undo.run();
            stun = 1.0; shake = 0.5; heldTask = null; climbing = false; Snd.play("alarm");
            addRage(16, "interrupted!");
            toast("INTERRUPTED!  " + who + ": \"" + msg + "\"");
            ft(px, py - 70, "ARGH", new Color(255, 90, 90));
        }
    }

    // =====================================================================
    //  CASH
    // =====================================================================
    int drawerValue() { int v = bigPile(); for (int i = 0; i < 4; i++) v += drawer[i] * DEN[i]; return v; }
    int drawerExcess() {
        int v = bigPile();
        for (int i = 0; i < 4; i++) v += Math.max(0, drawer[i] - FLOAT[i]) * DEN[i];
        return v;
    }
    void dropCash() {
        int v = drawerExcess();
        for (int i = 0; i < 4; i++) { int ex = Math.max(0, drawer[i] - FLOAT[i]); drawer[i] -= ex; safe[i] += ex; }
        safeBig += bigPile(); Arrays.fill(big, 0);
        health = Math.min(100, health + 3 + v / 100.0);
        toast("Dropped $" + v + " into the safe."); drawerWarned = false; Snd.play("clink");
    }
    void grabChange() {
        for (int i = 0; i < 4; i++) {
            int take = Math.min(TARGET[i] - drawer[i], safe[i]);
            if (take > 0) { drawer[i] += take; safe[i] -= take; }
        }
        toast("Bought cash: drawer restocked with small bills."); Snd.play("clink");
    }
    boolean canMake(int due) {
        for (int h = 0; h <= big[1] - bigGiven[1]; h++)
            for (int f = 0; f <= big[0] - bigGiven[0]; f++) {
                int d0 = due - h * 100 - f * 50;
                if (d0 < 0) break;
                for (int a = 0; a <= drawer[3] - given[3]; a++)
                    for (int b = 0; b <= drawer[2] - given[2]; b++)
                        for (int c = 0; c <= drawer[1] - given[1]; c++) {
                            int rest = d0 - a * 20 - b * 10 - c * 5;
                            if (rest >= 0 && rest <= drawer[0] - given[0]) return true;
                            if (rest < 0) break;
                        }
            }
        return false;
    }
    int givenSum() { int s = 0; for (int i = 0; i < 4; i++) s += given[i] * DEN[i]; return s + bigGiven[0] * 50 + bigGiven[1] * 100; }

    boolean panelOpen() {
        Customer c = front();
        return atRegister() && c != null && c.need == N_PAY;
    }

    void ringDone(Customer c) {
        if (c.type == T_CIG && c.flip && !c.flipped) {
            c.flipped = true; c.need = N_PACK; c.pat = Math.max(0.05, c.pat - 0.12); addRage(11, "wrong pack");
            say(c, "NO. Not that one. The OTHER one. Next to it.", 4);
            return;
        }
        clearGiven(); Snd.play("beep");
        if (c.type == T_BIG) addRage(14, "big bill");
        if (c.card) {   // no approval step: note it and move on
            say(c, "Tap tap. Card.", 2); coolLine(c); completePay(c);
            return;
        }
        c.need = N_PAY;
        say(c, "Here's a $" + c.bill + ".", 3);
    }

    /** A card payment is quick, so it helps cool the line, a lot on early nights and not at all from night 6. */
    void coolLine(Customer paying) {
        double boost = Math.max(0, 0.10 - 0.02 * (night - 1)), freeze = Math.max(0, 2.5 - 0.5 * (night - 1));
        int cooled = 0;
        for (Customer q : queue) {
            if (q == paying || q.need == N_SCRATCH) continue;
            q.pat = Math.min(1, q.pat + boost); q.calm = Math.max(q.calm, freeze); cooled++;
        }
        calmRage(Math.max(0, 5 - night));
        if (cooled > 0 && boost > 0) ft(px, py - 70, "card: line cooled", new Color(130, 230, 170));
    }

    void completePay(Customer c) {
        if (!c.card) {
            for (int i = 0; i < 4; i++) { drawer[i] -= given[i]; }
            for (int k = 0; k < 2; k++) big[k] -= bigGiven[k];
            boolean small = false;  // (bill goes in the drawer below)
            for (int i = 0; i < 4; i++) if (DEN[i] == c.bill) { drawer[i]++; small = true; }
            if (!small) for (int k = 0; k < 2; k++) if (BIGDEN[k] == c.bill) big[k]++;   // stays in the drawer, usable as change, until you DROP CASH
        }
        clearGiven();
        sales += c.total;
        if (c.type == T_SCRATCH) {
            c.need = N_SCRATCH; c.scratchT = c.tickets * 2.6;
            say(c, "Hold on, gotta scratch these RIGHT here.", 4);
            return;
        }
        finish(c, c.pat);
    }

    void submitChange() {
        Customer c = front();
        if (c == null || c.need != N_PAY) return;
        int due = c.bill - c.total;
        int g = givenSum();
        if (g == due) { completePay(c); return; }
        clearGiven(); Snd.play("buzz");
        c.pat = Math.max(0.03, c.pat - 0.15);
        say(c, g < due ? "That's NOT enough change!" : "Um, that's too much? ...keep it I guess.", 3);
        if (g > due) { tips -= 0; sales -= (g - due); }
    }

    void finish(Customer c, double satisfaction) {
        served++;
        if (satisfaction > 0.45) {
            combo++; bestCombo = Math.max(bestCombo, combo); happy++;
            bumpRush(0.07); Snd.play("kaching"); calmRage(7);
            int tip = (2 + rnd.nextInt(4)) * (1 + combo / 3);
            if (c.type == T_BIG || c.type == T_CIG || c.type == T_SCRATCH) tip *= 2;
            if (c.sodaHappy) tip += 2;   // appreciated the clean soda machine
            tips += tip;
            health = Math.min(100, health + 3.5 + Math.min(combo, 6) * 0.5);
            ft(c.x, c.y - 90, "+$" + tip + " TIP!" + (combo >= 3 ? "  x" + combo : ""), new Color(255, 225, 60));
            say(c, new String[]{"Have a good one!", "Thanks, friend!", "Nice! Cheers!", "Wow, fast!"}[rnd.nextInt(4)], 2);
        } else {
            health = Math.min(100, health + 1); bumpRush(0.01); Snd.play("beep");
            say(c, "Finally...", 2);
            combo = 0;
        }
        c.state = S_LEAVE; queue.remove(c); c.atSlot = false;
        c.path.clear();
    }

    void makeAngry(Customer c, String msg) {
        c.angry = true; angryCt++; combo = 0; bumpRush(-0.12); Snd.play("buzz"); addRage(14, "walkout");
        double pen = c.type == T_BIG ? 8 : 7;
        health = Math.max(0, health - pen);
        say(c, msg, 3);
        ft(c.x, c.y - 90, "-" + (int) pen + " health", new Color(255, 80, 80));
        queue.remove(c); c.atSlot = false; c.state = S_LEAVE; c.path.clear();
        if (c.type == T_BIG || c.type == T_SCRATCH) shake = 0.3;
    }

    // =====================================================================
    //  CUSTOMERS
    // =====================================================================
    void spawnLogic(double dt) {
        double mins = prog() * 480;
        // phase changes
        int pi = 0; for (int k = 0; k < PHASES.length; k++) if (mins >= PHASES[k].from) pi = k;
        if (pi != phaseIdx) {
            phaseIdx = pi; phaseName = PHASES[pi].name;
            toast(PHASES[pi].msg);
            if (pi > 0 && (phaseName.contains("QUIET") || phaseName.contains("LULL") || phaseName.contains("DEAD"))) startCalm();
        }
        // scheduled events (heads-up 12 seconds ahead)
        for (int k = 0; k < EVENTS.length; k++) {
            Event e = EVENTS[k];
            if (!evWarned[k] && mins >= e.min - 12 && e.min > 30) { evWarned[k] = true; toast("Heads up: " + e.msg); }
            if (!evFired[k] && mins >= e.min && burstLeft == 0) {
                evFired[k] = true; burstLeft = e.count + (night - 1) / 2; burstForce = e.type; burstT = 0;
            }
        }
        if (burstLeft > 0) {
            burstT -= dt;
            if (burstT <= 0) { spawn(burstForce); burstLeft--; burstT = 2.0 + rnd.nextDouble() * 2.5; }
            return;
        }
        // the background trickle, paced by the current phase and how well you're doing
        trickleT -= dt;
        if (trickleT <= 0) {
            int cap = 3 + (int) (effRush() * 6);
            if (customers.size() < cap) spawn(-1);
            trickleT = PHASES[pi].gap * lerp(1.2, 0.8, effRush()) * (0.8 + rnd.nextDouble() * 0.4) / nm();
        }
    }

    void startCalm() {
        // a lull comes with a little mess to deal with
        if (spills.size() < 2) addSpill(300 + rnd.nextInt(800), 340 + rnd.nextInt(290));
        soda = Math.min(100, soda + 6); rugDirt = Math.min(100, rugDirt + 8);
        Shelf s = shelves.get(rnd.nextInt(shelves.size())); s.face = Math.max(0, s.face - 35); s.faced = false;
    }

    // ---------- robbery ----------
    boolean robberPresent() { for (Customer c : customers) if (c.type == T_ROB) return true; return false; }

    void robberyLogic(double dt) {
        robCd -= dt;
        if (drawerValue() > 350 && robCd <= 0 && !robberPresent()) {
            robT += dt;
            if (robT >= robLimit() * 0.4 && !robWarn1) { robWarn1 = true; toast("Too much cash in the drawer! DROP CASH at the safe before someone notices."); }
            if (robT >= robLimit()) { robT = 0; robWarn1 = false; spawnRobber(); }
        } else {
            robT = Math.max(0, robT - dt * 2);
            if (robT == 0) robWarn1 = false;
        }
    }
    boolean robWarn1;

    void spawnRobber() {
        Customer c = new Customer();
        c.type = T_ROB; c.name = "ROBBER"; c.shirt = new Color(35, 35, 45); c.skin = new Color(55, 55, 65);
        c.x = DOOR_X; c.y = DOOR_Y; c.state = S_ROBGO; c.angry = true;
        c.wants = "ALL the cash in your drawer!";
        say(c, "THIS IS A ROBBERY!", 4);
        customers.add(c);
        toast("ROBBER! Drop the cash in the safe NOW, before he reaches the register!");
        Snd.play("alarm"); Snd.play("bell"); shake = 0.4;
    }

    void robbed(Customer c) {
        int amt = drawerValue();
        drawer = new int[4]; Arrays.fill(big, 0);
        robbedTotal += amt; robberies++; robCd = 70;
        health = Math.max(0, health - 15); combo = 0; shake = 0.9; bumpRush(-0.1);
        addRage(25, "robbed!");
        for (Customer q : queue) { q.pat = Math.max(0.05, q.pat - 0.3); say(q, "OH MY GOD!", 2.5); }
        say(c, amt > 0 ? "Thanks for the cash, sucker!" : "That's it?! Cheapskate!", 3);
        c.state = S_LEAVE; c.path.clear();
        toast(amt > 0 ? "ROBBED! He took $" + amt + " and your drawer is EMPTY. Go BUY CASH at the safe!" : "He found an empty drawer. Lucky you!");
        ft(px, py - 90, "-15 health", new Color(255, 80, 80));
        Snd.play("buzz"); Snd.play("alarm");
    }

    // ---------- co-worker ----------
    /** C pressed: if a call is allowed, pause and ask, showing the cost. */
    void requestCoworker() {
        if (scene != Scene.PLAY || help || cineOn || cwUsed || cwConfirm) return;
        if (rage <= 50) { toast("Too calm to call for backup. Your rage has to be over 50%."); return; }
        cwConfirm = true;
        up = down = left = right = eDown = false;
    }

    void callCoworker() {
        cwConfirm = false;
        if (scene != Scene.PLAY || cwUsed) return;
        int cost = tips / 2;
        tips -= cost; cwUsed = cwOn = true; cwLeaving = false; cwLeft = cwMinutes();
        int calmed = (int) Math.round(rage * cwCalmPct() / 100.0); calmRage(calmed);
        cwX = 585; cwY = 700; cwDir = 1;   // walks in through the front door
        Snd.play("bell");
        toast("Co-worker called in for " + cwMinutes() + " minutes" + (cost > 0 ? " (-$" + cost + " tips)" : "") + (calmed > 0 ? ". Rage -" + calmed : "") + ". Chores only, no customers or cash.");
        if (calmed > 0) ft(px, py - 95, "RAGE -" + calmed + "  phew", new Color(130, 230, 170));
    }

    void coworkerLogic(double dt) {
        cwMoving = cwWorking = false;
        if (!cwOn) return;
        if (!cwLeaving) {
            cwLeft -= dt;
            if (cwLeft <= 0) { cwLeaving = true; toast("Co-worker's shift is up. They're heading home."); }
        }
        if (cwLeaving) {   // walk back out the front door, then gone
            double dd = dist(cwX, cwY, 585, 700);
            if (dd < 12) { cwOn = false; return; }
            cwMoving = true; double st = Math.min(dd, CW_SPEED * dt);
            cwDir = 585 >= cwX ? 1 : -1;
            cwX += (585 - cwX) / dd * st; cwY += (700 - cwY) / dd * st;
            return;
        }
        // pick the most useful chore: spills, then soda, rug, empty shelves, messy shelves
        double tx = 0, ty = 0; int kind = 0; Spill sp = null; Shelf sh = null; double best = 1e9;
        for (Spill q : spills) { double d = dist(cwX, cwY, q.x, q.y); if (d < best) { best = d; sp = q; } }
        if (sp != null) { kind = 1; tx = sp.x; ty = sp.y; }
        else if (soda > 12) { kind = 2; tx = sodaR.getCenterX(); ty = sodaR.getMaxY() + 20; }
        else if (rugDirt > 12) { kind = 3; tx = rugR.getCenterX(); ty = rugR.getCenterY(); }
        else {
            for (Shelf q : shelves) if (q.stock <= 4) { double d = dist(cwX, cwY, q.standX(), q.standY()); if (d < best) { best = d; sh = q; } }
            if (sh != null) { kind = 4; tx = sh.standX(); ty = sh.standY(); }
            else {
                for (Shelf q : shelves) if (q.face < 92) { double d = dist(cwX, cwY, q.standX(), q.standY()); if (d < best) { best = d; sh = q; } }
                if (sh != null) { kind = 5; tx = sh.standX(); ty = sh.standY(); }
            }
        }
        if (kind == 0) return;   // nothing to do: stands around
        double d = dist(cwX, cwY, tx, ty);
        if (d > 10) {
            cwMoving = true;
            double step = Math.min(d, CW_SPEED * dt);
            cwDir = tx >= cwX ? 1 : -1;
            cwX += (tx - cwX) / d * step; cwY += (ty - cwY) / d * step;
            return;
        }
        cwWorking = true;
        double w = dt * CW_RATE;
        switch (kind) {
            case 1: sp.hp -= w * 42; if (sp.hp <= 0) { spills.remove(sp); health = Math.min(100, health + 5); } break;
            case 2: { double x = Math.min(soda, w * 18); soda -= x; health = Math.min(100, health + x * 0.08); break; }
            case 3: { double x = Math.min(rugDirt, w * 26); rugDirt -= x; health = Math.min(100, health + x * 0.06); break; }
            case 4: cwStockT += w; if (cwStockT >= 2.5) { cwStockT = 0; sh.stock = 10; health = Math.min(100, health + 4); } break;
            case 5: { double add = w * 32; sh.face = Math.min(100, sh.face + add); health = Math.min(100, health + add * 0.03);
                      if (sh.face >= 100 && !sh.faced) { sh.faced = true; health = Math.min(100, health + 2); } break; }
        }
    }

    void drawCoworker(Graphics2D g) {
        person(g, cwX, cwY, new Color(60, 120, 230), SKINS[2], cwMoving ? t * 14 : 0, 1, 5, cwDir, false);
        g.setColor(new Color(255, 255, 255, 200)); g.fillRect((int) cwX - 6, (int) (cwY - 36), 12, 3);
        text(g, "CO-WORKER", cwX, cwY - 72, fXs, new Color(150, 200, 255), true);
        if (cwWorking) text(g, "...", cwX, cwY - 84, fSm, Color.WHITE, true);
    }

    void drawCwConfirm(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 170)); g.fillRect(0, 0, W, H);
        double bx = W / 2.0 - 340, by = 220;
        blockHdr(g, bx, by, 680, 260, new Color(50, 60, 95), 24, 80);
        text(g, "CALL IN A CO-WORKER?", W / 2.0, by + 52, fBig, new Color(255, 230, 70), true);
        int cost = tips / 2;
        text(g, cost > 0 ? "It will cost $" + cost + "  (half of your $" + tips + " tips)." : "It's free right now: you have no tips to lose.", W / 2.0, by + 96, fMid, Color.WHITE, true);
        text(g, cwCalmPct() > 0 ? "Takes " + cwCalmPct() + "% off your rage right away." : "This deep into the week, they can't calm your rage. Chores only.", W / 2.0, by + 126, fSm, cwCalmPct() > 0 ? new Color(130, 230, 170) : new Color(255, 190, 120), true);
        text(g, "They stay " + cwMinutes() + " minutes and do chores only. One call per shift.", W / 2.0, by + 152, fSm, new Color(200, 205, 230), true);
        double yy = by + 178, bw = 200, bh = 52;
        cwBtn[0] = new Rectangle2D.Double(W / 2.0 - bw - 14, yy, bw, bh);
        cwBtn[1] = new Rectangle2D.Double(W / 2.0 + 14, yy, bw, bh);
        block(g, cwBtn[0].x, yy, bw, bh, new Color(24, 120, 66), 14);
        text(g, "YES  [ENTER]", cwBtn[0].getCenterX(), yy + 34, fMid, Color.WHITE, true);
        block(g, cwBtn[1].x, yy, bw, bh, new Color(165, 50, 50), 14);
        text(g, "NO  [ESC]", cwBtn[1].getCenterX(), yy + 34, fMid, Color.WHITE, true);
    }

    void drawCoworkerHint(Graphics2D g) {
        if (panelOpen()) return;   // the change panel sits over that corner
        if (cwOn) hintBox(g, cwLeaving ? "Co-worker is leaving" : "Co-worker: " + (int) Math.ceil(cwLeft) + " min left", 10, H - 120, new Color(150, 200, 255));
        else if (!cwUsed) {
            if (rage > 50) hintBox(g, "[C]  call co-worker  (-$" + tips / 2 + " tips, " + cwMinutes() + " min)", 10, H - 120, new Color(130, 230, 170));
            else hintBox(g, "Co-worker: available when rage is over 50%", 10, H - 120, new Color(120, 120, 140));
        }
    }

    // ---------- rage ----------
    void addRage(double d, String why) {
        d *= (1 + 0.08 * (night - 1)) * rageMult();
        rage = Math.min(100, rage + d);
        ft(px, py - 95, "RAGE +" + (int) d + (why.isEmpty() ? "" : "  " + why), new Color(255, 110, 90));
    }
    double rageMult() { return cwOn ? CW_RAGE_MULT : 1.0; }
    void calmRage(double d) { rage = Math.max(0, rage - d); }

    void rageLogic(double dt) {
        rage = Math.max(0, rage - dt * 0.6);
        if (!atRegister()) rage = Math.max(0, rage - dt * AWAY_DRAIN);   // stepping away from the register to do chores cools you off
        if (queue.size() >= 4) rage = Math.min(100, rage + dt * 2.0 * rageMult());
        Customer c0 = front();
        if (c0 != null && c0.need == N_SCRATCH && queue.size() >= 2) rage = Math.min(100, rage + dt * 2.4 * rageMult());
        // too angry for too long -> you snap
        if (rage >= 80 && panelOpen()) {
            // making change: the snap countdown is frozen (it's not fair to tick while you count out bills)
        } else if (rage >= 80 && heldTask != null) {
            // Starting a chore backs the snap timer off right away; you don't have to finish it first.
            snapT = Math.max(0, snapT - dt * 1.5);
        } else if (rage >= 80) {
            snapT += dt * (rage >= 95 ? 1.8 : 1.0);
            if (!snapWarned) { snapWarned = true; toast("You're about to SNAP! Do a chore or serve someone happy to cool off."); Snd.play("alarm"); }
            if (snapT >= SNAP_LIMIT) meltdown();
        } else {
            snapT = Math.max(0, snapT - dt * 1.5);
            if (rage < 65) snapWarned = false;
        }
    }

    void meltdown() {
        snaps++;
        snapT = 0; snapWarned = false; rage = 0;   // venting fully resets the rage meter
        health = Math.max(0, health - 12); combo = 0; stun = 2.0; shake = 1.0; heldTask = null; climbing = false;
        for (Customer c : new ArrayList<>(queue)) {
            c.pat = Math.max(0.04, c.pat - 0.45);
            say(c, new String[]{"Did that guy just YELL?!", "Whoa, calm down, buddy!", "I'm telling your manager!"}[rnd.nextInt(3)], 3);
        }
        for (Customer c : new ArrayList<>(customers)) if (c.type == T_ASK && c.state == S_ASK) { c.pat = 0; }
        ft(px, py - 80, "YOU SNAPPED!", new Color(255, 70, 70));
        Snd.play("buzz"); Snd.play("alarm");
        if (!startBeat()) toast("MELTDOWN! You lost it and whacked the air with a mop. -12 health");
    }

    // ---------- the mop beating ----------
    boolean startBeat() {
        Customer best = null; double bd = 1e9;
        for (Customer c : customers) {
            if (c.gone || c.state == S_LEAVE) continue;
            double d = dist(px, py, c.x, c.y);
            if (c.type == T_ROB) d -= 500;          // a robber is the juiciest target
            if (d < bd) { bd = d; best = c; }
        }
        if (best == null) return false;
        beatTarget = best; beatT = 2.4; beatHits = 0; beatSwing = 0.15; best.calm = 12;
        cineOn = true; cineT = 0; cineEnd = 0.9 + 2.4 + 1.0; hitFlash = 0;
        cineCaption = best.type == T_ROB ? "You go after the ROBBER..." : "You go after " + best.name + "...";
        focusX = (px + best.x) / 2; focusY = (py + best.y) / 2;
        toastT = 0;
        Snd.play("alarm");
        return true;
    }

    void beatLogic(double dt) {
        if (beatT <= 0) return;
        Customer c = beatTarget;
        if (c == null || c.gone) { beatT = 0; beatTarget = null; cineEnd = Math.min(cineEnd, cineT + 0.5); return; }
        stun = Math.max(stun, 0.3);
        focusX = (px + c.x) / 2; focusY = (py + c.y) / 2;
        if (cineOn && cineT < 0.9) return;     // the SNAP! intro plays before the swinging starts
        beatT -= dt;
        double d = dist(px, py, c.x, c.y);
        if (d > 50) {
            double sp = 520 * dt;
            px += (c.x - px) / d * Math.min(d - 48, sp); py += (c.y - py) / d * Math.min(d - 48, sp);
        } else {
            beatSwing += dt;
            if (beatSwing >= 0.5) {
                beatSwing = 0; beatHits++;
                shake = 0.6; hitFlash = 0.4; hitX = W / 2.0 + rnd.nextInt(260) - 130; hitY = H / 2.0 - 40 + rnd.nextInt(120) - 60;
                hitWord = new String[]{"WHACK!", "BONK!", "THWACK!", "SPLAT!", "BAM!"}[rnd.nextInt(5)];
                Snd.play("thud"); Snd.play("pick"); if (beatHits == 1) Snd.play("buzz");
                c.x += (rnd.nextDouble() - 0.5) * 14;
            }
        }
        if (d > 0.5) facingDir = c.x >= px ? 1 : -1;
        if (beatHits >= 4 || beatT <= 0) finishBeat(c);
    }

    void finishBeat(Customer c) {
        beatT = 0; beatTarget = null;
        cineEnd = cineT + 1.1;
        cineCaption = c.type == T_ROB ? "The robber thinks twice." : "You feel better. " + c.name + " does not.";
        if (c.type == T_ROB) {
            c.state = S_LEAVE; c.path.clear(); say(c, "OW! This isn't worth it!", 3);
            tips += 25; health = Math.min(100, health + 6); robCd = 60;
            toast("You clobbered the ROBBER! He drops the plan (and $25 for your trouble).");
            return;
        }
        c.angry = true; angryCt++; bumpRush(-0.1); c.pat = 0;
        queue.remove(c); c.atSlot = false; c.state = S_LEAVE; c.path.clear();
        say(c, "OW!! I'm calling my LAWYER!", 4);
        // the lawyer gets paid out of your tips: 25% (a bit more each night), $10 minimum, never more than you have
        double frac = Math.min(0.5, 0.25 + 0.03 * (night - 1));
        int fine = Math.min(tips, Math.max(10, (int) Math.round(tips * frac)));
        tips -= fine;
        cineCaption = "You feel better. " + c.name + " does not.  Settlement: -$" + fine;
        toast(fine > 0 ? "You beat " + c.name + " with a mop. Lawyer's settlement: -$" + fine + " from your tips."
                       : "You beat " + c.name + " with a mop. Good thing you had no tips to lose.");
        if (fine > 0) ft(px, py - 100, "-$" + fine + " LAWSUIT", new Color(255, 90, 90));
    }

    /** Difficulty: mostly driven by how well you're doing, with a gentle push from the clock. */
    double effRush() { return Math.min(1, rush * 0.85 + prog() * 0.15); }
    void bumpRush(double d) { rush = Math.max(0, Math.min(1, rush + d)); }

    void spawn(int force) {
        Customer c = new Customer();
        c.name = NAMES[rnd.nextInt(NAMES.length)];
        c.shirt = SHIRTS[rnd.nextInt(SHIRTS.length)];
        c.skin = SKINS[rnd.nextInt(SKINS.length)];
        c.x = DOOR_X + rnd.nextInt(30) - 15; c.y = DOOR_Y;
        int r = rnd.nextInt(100);
        c.type = r < 36 ? T_NORMAL : r < 54 ? T_BIG : r < 70 ? T_CIG : r < 82 ? T_SCRATCH : T_ASK;
        // learning curve: the first few customers are plain, cigarettes come next, the nasty types after you've warmed up
        int gate = nSpawned + (night - 1) * 20;
        if (gate < 5 && c.type > T_BIG) c.type = rnd.nextInt(100) < 25 ? T_BIG : T_NORMAL;
        else if (gate < 9 && (c.type == T_SCRATCH || c.type == T_ASK)) c.type = T_CIG;
        else if (effRush() < 0.12 && (c.type == T_SCRATCH || c.type == T_ASK)) c.type = rnd.nextBoolean() ? T_NORMAL : T_BIG;
        if (force >= 0) c.type = force;
        nSpawned++;
        double hurry = lerp(1.9, 1.0, effRush()) / nm();   // patience: generous early, shrinking as the rush level rises
        switch (c.type) {
            case T_NORMAL: c.patMax = 42 * hurry; break;
            case T_BIG: c.patMax = 36 * hurry; break;
            case T_CIG: c.patMax = 50 * hurry; break;
            case T_SCRATCH: c.patMax = 36 * hurry; break;
            case T_BEER: c.patMax = 40 * hurry; break;
            default: c.patMax = 11;
        }
        if (c.type == T_ASK) {
            c.state = S_ASKGO;
            String[] qs = {"Where's the bathroom?", "Is the coffee fresh?", "Got a pen I can borrow?",
                    "Do you have lottery?", "What's the WiFi password?", "Can you make change for a dollar?",
                    "Where's the milk?", "How much is this?", "Is this open 24 hours?", "Do you know a good plumber?"};
            c.q = qs[rnd.nextInt(qs.length)];
        } else {
            // pick a shelf with stock
            List<Integer> ok = new ArrayList<>();
            for (int i = 0; i < shelves.size(); i++) if (shelves.get(i).stock > 0) ok.add(i);
            c.state = S_SHOP;
            c.shelf = ok.isEmpty() ? rnd.nextInt(shelves.size()) : ok.get(rnd.nextInt(ok.size()));
            if (c.type == T_BEER && shelves.get(5).stock > 0) c.shelf = 5;
            Shelf s = shelves.get(c.shelf);
            c.path.add(new double[]{585, 640});
            c.path.add(new double[]{585, s.standY()});
            c.path.add(new double[]{s.standX(), s.standY()});
            c.browse = 1.5 + rnd.nextDouble() * 2;
            if (c.type != T_BEER && rnd.nextInt(100) < 30) {          // some people grab a fountain drink first
                c.sodaUser = true; c.path.clear();
                c.path.add(new double[]{585, 600});
                c.path.add(new double[]{sodaR.getCenterX(), sodaR.getMaxY() + 42});
            }
            newOrder(c);
        }
        customers.add(c);
        Snd.play("bell");
    }

    void newOrder(Customer c) {
        Arrays.fill(c.pr, 0);
        switch (c.type) {
            case T_NORMAL:
                c.total = 3 + rnd.nextInt(16); c.card = rnd.nextInt(100) < 30;
                int[] bills = {5, 10, 20, 20};
                c.bill = 20;
                for (int b : bills) if (b >= c.total && rnd.nextInt(3) > 0) { c.bill = b; break; }
                if (c.bill < c.total) c.bill = 20;
                c.need = N_RING;
                break;
            case T_BIG:
                c.total = 1 + rnd.nextInt(4); c.card = false; c.bill = rnd.nextInt(100) < 55 ? 100 : 50; c.need = N_RING;
                break;
            case T_CIG:
                c.total = 12 + rnd.nextInt(3); c.card = false; c.bill = 20; c.need = N_PACK; c.flip = rnd.nextInt(100) < 45;
                break;
            case T_SCRATCH:
                if (c.rounds == 0) c.rounds = 2 + rnd.nextInt(2);
                c.tickets = 2 + rnd.nextInt(4); c.total = c.tickets * 5; c.card = false;
                c.bill = c.total <= 15 ? 20 : 50; c.need = N_RING;
                break;
            case T_BEER:
                c.total = 8 + rnd.nextInt(7); c.card = rnd.nextInt(100) < 40; c.bill = 20; c.need = N_ID;
                c.idDur = rnd.nextInt(100) < 30 ? 2.6 : 1.0;   // some of them dig for it forever
                break;
        }
        switch (c.type) {
            case T_NORMAL: c.wants = "snacks and drinks ($" + c.total + ")"; break;
            case T_BIG: c.wants = "a pack of gum ($" + c.total + ") - paying with a $" + c.bill; break;
            case T_CIG: c.wants = "a pack of cigarettes from the TOP shelf"; break;
            case T_SCRATCH: c.wants = c.tickets + " scratch-off tickets ($" + c.total + ")"; break;
            case T_BEER: c.wants = "a 6-pack of beer ($" + c.total + ") - needs an ID check"; break;
        }
        if (c.type == T_BEER) say(c, c.idDur > 2 ? "6-pack of beer. ID? ...it's in here somewhere." : "6-pack of Bland Light. Here's my ID.", 7);
        else if (c.type == T_BIG) say(c, "Can you break a $" + c.bill + "? Just a pack of gum.", 6);
        else if (c.type == T_CIG) say(c, new String[]{"Pack of Marbleboro Golds. TOP shelf. Way up there.", "Newtwarts, the ones on the very top.", "Caramel Crush, top row, up there."}[rnd.nextInt(3)], 8);
        else if (c.type == T_SCRATCH) say(c, c.round == 0 ? "Gimme " + c.tickets + " scratch-offs. The $5 ones." : "Same again! " + c.tickets + " more!", 7);
        else say(c, "Just this, please.", 2.5);
    }

    void say(Customer c, String s, double dur) { c.say = s; c.sayT = dur; }

    boolean walk(Customer c, double tx, double ty, double dt, double sp) {
        double dx = tx - c.x, dy = ty - c.y, d = Math.hypot(dx, dy);
        if (d < 3) return true;
        double s = Math.min(d, sp * dt);
        c.x += dx / d * s; c.y += dy / d * s; if (Math.abs(dx) > 1) c.dir = dx > 0 ? 1 : -1;
        c.bob += dt * 12;
        return false;
    }

    void updateCustomer(Customer c, double dt) {
        c.sayT -= dt;
        double sp = 105 + (c.angry ? 40 : 0);
        switch (c.state) {
            case S_SHOP:
                if (c.path.isEmpty()) { c.state = S_BROWSE; break; }
                if (walk(c, c.path.get(0)[0], c.path.get(0)[1], dt, sp)) c.path.remove(0);
                break;
            case S_BROWSE:
                c.browse -= dt;
                if (c.browse <= 0) {
                    if (c.sodaUser) {
                        soda = Math.min(100, soda + 1.5);
                        if (soda > 60) {
                            c.pat = Math.max(0.05, c.pat - 0.2); addRage(4, "gross soda machine");
                            say(c, new String[]{"Ugh, this machine is DISGUSTING.", "Sticky. Everything is sticky.", "Do you EVER clean this?"}[rnd.nextInt(3)], 3.5);
                        } else if (soda < 25) {
                            c.pat = Math.min(1, c.pat + 0.1); c.sodaHappy = true;
                            say(c, "Oh nice, the soda machine's clean.", 3);
                        }
                        rugDirt = Math.min(100, rugDirt + 4);
                        c.state = S_QUEUE; queue.add(c);
                        break;
                    }
                    Shelf s = shelves.get(c.shelf);
                    if (s.stock <= 0) {
                        c.angry = true; angryCt++; combo = 0; health = Math.max(0, health - 4); bumpRush(-0.06); Snd.play("buzz"); addRage(9, "out of stock");
                        say(c, "You're OUT of " + s.name + "?! Unbelievable.", 3);
                        ft(c.x, c.y - 90, "-4 health (out of stock)", new Color(255, 80, 80));
                        c.state = S_LEAVE; break;
                    }
                    s.stock--; s.face = Math.max(0, s.face - (12 + rnd.nextInt(25)));
                    if (s.face < 92) s.faced = false;
                    rugDirt = Math.min(100, rugDirt + 4);
                    if (rnd.nextInt(100) < 20) addSpill(c.x + rnd.nextInt(20) - 10, c.y + 10);
                    c.state = S_QUEUE; queue.add(c);
                    if (queue.size() > 6) { /* overflow: still allowed, just stacks */ }
                }
                break;
            case S_QUEUE: {
                int idx = queue.indexOf(c);
                double sx = 585, sy = 338 + Math.min(idx, 6) * 54;
                boolean arrived = walk(c, sx, sy, dt, sp);
                c.atSlot = arrived && idx == 0;
                if (c.atSlot && !c.seen) {
                    c.seen = true;
                    if (c.type == T_BIG) addRage(20, "big bill customer");
                    else if (c.type == T_SCRATCH) addRage(8, "scratcher guy");
                }
                c.paused = false;
                boolean scratching = c.need == N_SCRATCH;
                if (scratching) {
                    c.scratchT -= dt;
                    if (c.scratchT <= 0) {
                        c.round++;
                        if (c.round >= c.rounds) { finish(c, c.pat); break; }
                        newOrder(c);
                    }
                } else if (c.calm > 0) {
                    c.calm -= dt;
                } else if (idx == 0 && c.need == N_PAY && c.atSlot && (panelOpen() || !canMake(c.bill - c.total - givenSum()))) {
                    c.paused = true;   // making change (or stuck without small bills): their clock waits
                } else if (arrived || idx > 0) {
                    c.pat -= dt / c.patMax * (idx == 0 ? 1.0 : 0.55) * (1 + rage / 250);  // an angry clerk makes everyone antsy
                }
                if (c.atSlot && c.pat < 0.5 && !c.yelled && !scratching) {
                    c.yelled = true;
                    String[] ys = {"HELLO?! Anyone working here?!", "I don't have all day!", "Seriously?! I'm RIGHT here!", "Unreal. UNREAL."};
                    String y = ys[rnd.nextInt(ys.length)];
                    say(c, y, 3);
                    if (!atRegister()) interrupt(c.name, y);
                }
                if (c.pat <= 0) makeAngry(c, new String[]{"Forget it! I'm leaving a review!", "Worst store EVER.", "I'm calling corporate!"}[rnd.nextInt(3)]);
                break;
            }
            case S_ASKGO:
                if (walk(c, px - 40 * facingDir, py + 20, dt, 135) || dist(c.x, c.y, px, py) < 58) {
                    c.state = S_ASK; say(c, c.q, 8);
                    interrupt(c.name, c.q);
                }
                break;
            case S_ASK:
                if (dist(c.x, c.y, px, py) > 62) walk(c, px - 40 * facingDir, py + 20, dt, 150);
                c.pat -= dt / c.patMax;
                c.sayT = 1;
                if (c.pat <= 0) {
                    c.angry = true; angryCt++; combo = 0; health = Math.max(0, health - 5); bumpRush(-0.08); Snd.play("buzz"); addRage(9, "ignored");
                    say(c, "Wow. Ignoring me. Great service.", 3);
                    ft(c.x, c.y - 90, "-5 health", new Color(255, 80, 80));
                    c.state = S_LEAVE;
                }
                break;
            case S_ROBGO:
                if (walk(c, REG_X, 300, dt, 150)) { c.state = S_ROBTAKE; c.browse = 2.2; say(c, "EMPTY THE DRAWER! NOW!", 3); }
                break;
            case S_ROBTAKE:
                c.browse -= dt; c.sayT = 1;
                if (c.browse <= 0) robbed(c);
                break;
            case S_LEAVE:
                if (walk(c, 585, DOOR_Y, dt, c.type == T_ROB ? 170 : sp)) c.gone = true;
                break;
        }
        // customers dropping drinks as they leave
    }

    // =====================================================================
    //  WORLD EVENTS / HEALTH
    // =====================================================================
    void addSpill(double x, double y) {
        Spill s = new Spill();
        s.x = Math.max(80, Math.min(1200, x)); s.y = Math.max(300, Math.min(650, y));
        spills.add(s);
    }

    void worldEvents(double dt) {
        soda = Math.min(100, soda + dt * 0.45);   // slow creep; customers add more
        rugDirt = Math.min(100, rugDirt + dt * 0.5);
        // a neglected soda machine leaks sticky puddles
        if (soda > 70) {
            sodaLeakT -= dt;
            if (sodaLeakT <= 0 && spills.size() < 6) {
                Spill sp = new Spill();
                sp.x = sodaR.getCenterX() + rnd.nextInt(110) - 55; sp.y = sodaR.getMaxY() + 38 + rnd.nextInt(30);
                spills.add(sp);
                sodaLeakT = 20 / nm() * (0.8 + rnd.nextDouble() * 0.4);
                if (!sodaLeakWarned) { sodaLeakWarned = true; toast("The soda machine is leaking! Clean it to stop the puddles."); }
            }
        } else sodaLeakT = Math.max(sodaLeakT, 10);
        spillT -= dt;
        if (spillT <= 0 && spills.size() < 5) {
            addSpill(300 + rnd.nextInt(800), 330 + rnd.nextInt(300));
            spillT = lerp(26, 14, prog()) + rnd.nextInt(10);
            toast("Cleanup in aisle " + (1 + rnd.nextInt(5)) + "!");
        }
        if (drawerValue() > 350 && !drawerWarned) { drawerWarned = true; toast("Drawer is over $350 -- DROP IT in the safe!"); }
    }

    void healthLogic(double dt) {
        double d = 0.09 + 0.08 * prog();
        d += Math.min(4, spills.size()) * 0.045;
        if (soda > 70) d += 0.15;
        if (rugDirt > 70) d += 0.12;
        for (Shelf s : shelves) { if (s.stock == 0) d += 0.12; if (s.face < 40) d += 0.04; }
        if (drawerValue() > 400) d += 0.45;
        if (rage > 70) d += 0.2;
        d *= night > 1 ? 1 + 0.08 * (night - 1) : lerp(0.5, 1.0, t / 180.0);   // night 1: gentle first 3 minutes; later nights drain faster
        health = Math.max(0, Math.min(100, health - d * dt));
        if (health <= 0) { scene = Scene.OVER; lastShiftScore = shiftScore(false); Snd.play("lose"); }   // a shift you got fired in does not count toward the running total
    }

    void toast(String s) { toast = s; toastT = 3.2; }
    void ft(double x, double y, String s, Color c) { FT f = new FT(); f.x = x; f.y = y; f.s = s; f.c = c; fts.add(f); }

    // =====================================================================
    //  INPUT
    // =====================================================================
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_M) { Snd.muted = !Snd.muted; if (scene == Scene.PLAY) toast(Snd.muted ? "Sound OFF" : "Sound ON"); return; }
        if (cwConfirm) {   // the game is paused on the co-worker question
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE || k == KeyEvent.VK_Y) callCoworker();
            else if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_N || k == KeyEvent.VK_C) cwConfirm = false;
            return;
        }
        if (k == KeyEvent.VK_H || k == KeyEvent.VK_F1 || k == KeyEvent.VK_P || (help && k == KeyEvent.VK_ESCAPE)) {
            if (k == KeyEvent.VK_P && scene != Scene.PLAY) return;
            help = !help; up = down = left = right = eDown = false; return;
        }
        if (help) return;
        if (scene == Scene.TITLE || scene == Scene.OVER || scene == Scene.WIN) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startNext();
            else if (scene != Scene.TITLE && (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_Q)) System.exit(0);
            return;
        }
        switch (k) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP: up = true; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: down = true; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: left = true; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = true; break;
            case KeyEvent.VK_SHIFT: sprint = true; break;
            case KeyEvent.VK_E: if (!eDown) ePressed = true; eDown = true; break;
            case KeyEvent.VK_Q: qPressed = true; break;
            case KeyEvent.VK_F: fPressed = true; break;
            case KeyEvent.VK_C: requestCoworker(); break;
            case KeyEvent.VK_1: addGiven(0); break;
            case KeyEvent.VK_2: addGiven(1); break;
            case KeyEvent.VK_3: addGiven(2); break;
            case KeyEvent.VK_4: addGiven(3); break;
            case KeyEvent.VK_5: addGivenBig(0); break;
            case KeyEvent.VK_6: addGivenBig(1); break;
            case KeyEvent.VK_BACK_SPACE: clearGiven(); break;
            case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE: if (panelOpen()) submitChange(); break;
        }
    }
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP: up = false; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: down = false; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: left = false; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = false; break;
            case KeyEvent.VK_SHIFT: sprint = false; break;
            case KeyEvent.VK_E: eDown = false; break;
        }
    }
    public void keyTyped(KeyEvent e) {}

    void addGiven(int i) {
        if (!panelOpen()) return;
        Customer c = front();
        if (givenSum() + DEN[i] > c.bill - c.total) return;
        if (given[i] < drawer[i]) { given[i]++; Snd.play("click"); }
    }

    void addGivenBig(int k) {
        if (!panelOpen()) return;
        Customer c = front();
        if (givenSum() + BIGDEN[k] > c.bill - c.total) return;
        if (bigGiven[k] < big[k]) { bigGiven[k]++; Snd.play("click"); }
    }

    public void mousePressed(MouseEvent e) {
        if (cwConfirm) {
            if (cwBtn[0] != null && cwBtn[0].contains(e.getX(), e.getY())) callCoworker();
            else if (cwBtn[1] != null && cwBtn[1].contains(e.getX(), e.getY())) cwConfirm = false;
            return;
        }
        if (scene == Scene.PLAY && panelOpen()) {
            for (int i = 0; i < 8; i++)
                if (panelBtn[i] != null && panelBtn[i].contains(e.getX(), e.getY())) {
                    if (i < 4) addGiven(i); else if (i == 4) clearGiven(); else if (i == 5) submitChange(); else addGivenBig(i - 6);
                }
        } else if (scene == Scene.TITLE && !help) startNext();
        else if ((scene == Scene.OVER || scene == Scene.WIN) && !help) {
            if (endBtn[0] != null && endBtn[0].contains(e.getX(), e.getY())) startNext();
            else if (endBtn[1] != null && endBtn[1].contains(e.getX(), e.getY())) System.exit(0);
        }
    }
    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    // =====================================================================
    //  RENDERING
    // =====================================================================
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        draw((Graphics2D) g0);
    }

    void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(40, 44, 70)); g.fillRect(0, 0, W, H);
        if (scene == Scene.TITLE) { drawTitle(g); if (help) drawHelp(g); return; }
        AffineTransform at = g.getTransform();
        if (shake > 0) g.translate((rnd.nextDouble() - 0.5) * 12 * shake, (rnd.nextDouble() - 0.5) * 12 * shake);
        if (cineOn && scene == Scene.PLAY) {            // camera pushes in on the fight
            double k = Math.min(Math.min(1, cineT / 0.5), Math.min(1, Math.max(0, (cineEnd - cineT) / 0.5)));
            double z = 1 + 0.6 * k * k * (3 - 2 * k);
            double fx = Math.max(W / (2 * z), Math.min(W - W / (2 * z), focusX)), fy = Math.max(H / (2 * z), Math.min(H - H / (2 * z), focusY));
            g.translate(W / 2.0, H / 2.0); g.scale(z, z); g.translate(-fx, -fy);
        }
        drawStore(g);
        // depth-sorted actors
        List<Object> actors = new ArrayList<>(customers);
        actors.add("player");
        if (cwOn) actors.add("coworker");
        actors.sort(Comparator.comparingDouble(o -> o instanceof Customer ? ((Customer) o).y : "coworker".equals(o) ? cwY : py));
        for (Object o : actors) {
            if (o instanceof Customer) drawCustomer(g, (Customer) o); else if ("coworker".equals(o)) drawCoworker(g); else drawPlayer(g);
        }
        for (Customer c : customers) drawBubble(g, c);
        for (FT f : fts) {
            g.setFont(fMid); int a = (int) Math.max(0, Math.min(255, f.life * 255));
            g.setColor(new Color(0, 0, 0, a / 2)); g.drawString(f.s, (int) f.x - g.getFontMetrics().stringWidth(f.s) / 2 + 2, (int) f.y + 2);
            g.setColor(new Color(f.c.getRed(), f.c.getGreen(), f.c.getBlue(), a));
            g.drawString(f.s, (int) f.x - g.getFontMetrics().stringWidth(f.s) / 2, (int) f.y);
        }
        g.setTransform(at);
        boolean cine = cineOn && scene == Scene.PLAY;
        if (!cine) { drawHud(g); if (panelOpen()) drawPanel(g); }
        if (cine) drawCine(g);
        if (scene == Scene.OVER) drawEnd(g, false);
        if (scene == Scene.WIN) drawEnd(g, true);
        if (help) drawHelp(g);
        if (cwConfirm) drawCwConfirm(g);
    }

    void bigText(Graphics2D g, String s, double cx, double cy, int size, double rot, Color fill) {
        Font f = new Font("Arial Rounded MT Bold", Font.BOLD, size);
        AffineTransform old = g.getTransform();
        g.translate(cx, cy); g.rotate(rot);
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        g.setColor(OUT);
        int o = Math.max(3, size / 14);
        for (int dx = -o; dx <= o; dx += o) for (int dy = -o; dy <= o; dy += o) g.drawString(s, -w / 2 + dx, dy + size / 3);
        g.setColor(fill); g.drawString(s, -w / 2, size / 3);
        g.setTransform(old);
    }

    void drawCine(Graphics2D g) {
        double in = Math.min(1, cineT / 0.35), out = Math.min(1, Math.max(0, (cineEnd - cineT) / 0.4));
        double k = Math.min(in, out);
        // red vignette
        g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H / 2.0), (float) (W * 0.62), new float[]{0.4f, 1f},
                new Color[]{new Color(170, 0, 0, 0), new Color(150, 0, 0, (int) (190 * k))}));
        g.fillRect(0, 0, W, H);
        // speed lines
        g.setStroke(new BasicStroke(3f));
        Random sr = new Random((long) (cineT * 14));
        for (int i = 0; i < 26; i++) {
            double a = sr.nextDouble() * Math.PI * 2, r0 = 330 + sr.nextDouble() * 160, r1 = r0 + 140 + sr.nextDouble() * 220;
            g.setColor(new Color(255, 255, 255, (int) (110 * k)));
            g.drawLine((int) (W / 2 + Math.cos(a) * r0), (int) (H / 2 + Math.sin(a) * r0), (int) (W / 2 + Math.cos(a) * r1), (int) (H / 2 + Math.sin(a) * r1));
        }
        // letterbox bars
        int bar = (int) (86 * k);
        g.setColor(Color.BLACK); g.fillRect(0, 0, W, bar); g.fillRect(0, H - bar, W, bar);
        // intro: SNAP!
        if (cineT < 1.15) {
            double pulse = 1 + 0.12 * Math.sin(cineT * 30);
            double sh = (1.15 - cineT) * 6;
            bigText(g, "SNAP!!!", W / 2.0 + (rnd.nextDouble() - 0.5) * sh, H / 2.0 - 10 + (rnd.nextDouble() - 0.5) * sh, (int) (150 * pulse), -0.1, new Color(255, 70, 50));
            text(g, "that's the last straw.", W / 2.0, H / 2.0 + 90, fMid, Color.WHITE, true);
        }
        // each hit: giant mop sweeps across, flash, starburst, comic word
        if (hitFlash > 0) {
            double p = 1 - hitFlash / 0.4;
            g.setColor(new Color(255, 255, 255, (int) (80 * hitFlash / 0.4)));
            g.fillRect(0, bar, W, H - 2 * bar);
            AffineTransform old = g.getTransform();
            g.translate(W * 0.78, -40); g.rotate(-1.05 + 1.5 * p);
            g.setColor(new Color(150, 100, 50)); g.setStroke(new BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(0, 0, 0, 440);
            g.setColor(new Color(235, 235, 245)); g.fillRoundRect(-90, 420, 180, 120, 36, 36);
            g.setColor(new Color(200, 200, 215));
            for (int i = -70; i <= 70; i += 28) g.fillRoundRect(i - 8, 520, 16, 70, 8, 8);
            g.setColor(OUT); g.setStroke(new BasicStroke(6f)); g.drawRoundRect(-90, 420, 180, 120, 36, 36);
            g.setTransform(old);
            double sc = 0.6 + 0.8 * Math.min(1, p * 3);
            Polygon star = new Polygon();
            for (int i = 0; i < 28; i++) { double r = (i % 2 == 0 ? 190 : 110) * sc, a = i * Math.PI / 14 + 0.2; star.addPoint((int) (hitX + Math.cos(a) * r), (int) (hitY + Math.sin(a) * r)); }
            g.setColor(new Color(255, 225, 50)); g.fillPolygon(star);
            g.setColor(new Color(235, 50, 40)); g.setStroke(new BasicStroke(7f)); g.drawPolygon(star);
            bigText(g, hitWord, hitX, hitY, (int) (84 * sc), -0.14, Color.WHITE);
        }
        // outro caption on the bottom bar
        if (cineT > 0.9 && beatT <= 0 && bar > 20)
            text(g, cineCaption, W / 2.0, H - bar / 2.0 + 8, fMid, Color.WHITE, true);
    }

    void drawHelp(Graphics2D g) {
        g.setColor(new Color(10, 12, 30, 242)); g.fillRect(0, 0, W, H);
        text(g, "HOW TO SURVIVE THE NIGHT SHIFT", W / 2.0, 62, fBig, new Color(255, 230, 70), true);
        text(g, "press H, F1 or ESC to go back" + (scene == Scene.PLAY ? " (game is paused)" : ""), W / 2.0, 90, fSm, new Color(200, 205, 230), true);
        String[][] colL = {
            {"CONTROLS", ""},
            {"W A S D / arrows", "walk  (Shift = sprint)"},
            {"E  (tap)", "grab box, hand over pack, stock"},
            {"E  (HOLD)", "chores, ring up, climb for the cigs"},
            {"Q", "put down whatever you're carrying"},
            {"F", "\"Be right with you!\" calms the line (15s+ cooldown; weaker each night)"},
            {"C", "co-worker: rage over 50%, once a shift, costs half your tips"},
            {"1 - 6  / mouse", "add $1/$5/$10/$20/$50/$100  (Backspace = clear)"},
            {"ENTER / SPACE", "hand over the change (must be EXACT)"},
            {"H  /  M  /  P", "help  /  mute sound  /  pause"},
            {"", ""},
            {"THE YELLOW PROMPT", "at the bottom tells you what E will do right now"},
            {"THE DASHED RING", "marks the thing you're about to use"},
            {"EACH NIGHT", "you survive makes the next one ~10% harder"},
        };
        String[][] colR = {
            {"KEEPING THE STORE ALIVE", ""},
            {"STORE HEALTH", "drains all night. Fill it with chores:"},
            {"  Mop spills", "stand on the blue puddle, hold E"},
            {"  Soda machine / rug", "hold E when dirty (a dirty soda machine leaks!)"},
            {"  Stock shelves", "box from BACK ROOM, tap E at a shelf"},
            {"  Front-face", "hold E at a messy shelf"},
            {"  Safe: DROP CASH", "move extra/big bills to the safe (over $350 = bad)"},
            {"  Safe: BUY CASH", "restock small bills when the drawer runs low"},
            {"TRAFFIC", "bar at bottom right: climbs when you do well,"},
            {"", "drops when people storm out."},
            {"ROBBERY", "drawer over $350 for ~25s: a robber comes. DROP CASH!"},
            {"TIPS & COMBO", "serve fast (green bar) for tips"},
            {"RAGE-O-METER", "big bills, interruptions, walkouts raise it;"},
            {"", "chores, happy customers and F cool it."},
            {"", "Above 80% for 7s you SNAP: you grab the mop"},
            {"", "and beat the nearest customer (-12 health)."},
        };
        drawHelpCol(g, colL, 70, 125);
        drawHelpCol(g, colR, 670, 125);
        double y = 516;
        block(g, 60, y, 1160, 192, new Color(50, 60, 95), 20);
        text(g, "CUSTOMER FIELD GUIDE", 90, y + 32, fMid, new Color(255, 230, 70), false);
        String[] f = {
            "Top hat (big bills):  pays with $50/$100. Make exact change. Out of small bills? Run to the safe and BUY CASH.",
            "Red cap (cigarettes):  wants the TOP shelf. Stand at the dashed ring under the cig wall and HOLD E to climb. Slow, and so is ringing them up.",
            "                                 Tip: climb as soon as they walk in for a BONUS (+$5, health). Sometimes they want the OTHER one. Yes, again.",
            "Green visor (scratchers):  wants scratch-offs. Ring them up, then they scratch them AT the counter, blocking the line. Then buy more.",
            "Blue cap (beer):  needs an ID check first (HOLD E), then ring up. Some dig for their ID forever.",
            "Hard hat (asker):  follows you and asks questions. Hold E on them to answer -- they interrupt your chores!",
            "Anyone yelling at the counter will also interrupt you. Interrupts cancel your chore progress, so finish fast."};
        for (int i = 0; i < f.length; i++) text(g, f[i], 90, y + 56 + i * 20, fSm, Color.WHITE, false);
    }

    void drawHelpCol(Graphics2D g, String[][] rows, int x, int y) {
        for (int i = 0; i < rows.length; i++) {
            if (i == 0) { text(g, rows[i][0], x, y, fMid, new Color(255, 230, 70), false); continue; }
            text(g, rows[i][0], x, y + i * 25, fSm, new Color(130, 230, 170), false);
            text(g, rows[i][1], x + 190, y + i * 25, fSm, Color.WHITE, false);
        }
    }

    void block(Graphics2D g, double x, double y, double w, double h, Color c, int arc) { blockHdr(g, x, y, w, h, c, arc, Math.max(4, h * 0.28)); }
    /** A block whose lighter header band is hdr pixels tall (for big panels, where a proportional band would cut through the text). */
    void blockHdr(Graphics2D g, double x, double y, double w, double h, Color c, int arc, double hdr) {
        g.setColor(new Color(0, 0, 0, 50)); g.fill(new RoundRectangle2D.Double(x + 4, y + 6, w, h, arc, arc));
        g.setColor(c); g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        g.setColor(c.brighter()); g.fill(new RoundRectangle2D.Double(x + 2, y + 2, w - 4, hdr, arc, arc));
        g.setColor(OUT); g.setStroke(new BasicStroke(3)); g.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
    }

    void text(Graphics2D g, String s, double x, double y, Font f, Color c, boolean center) {
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        int xx = (int) (center ? x - w / 2.0 : x);
        // drop shadow only behind light text; on dark text it just smears
        if (c.getRed() + c.getGreen() + c.getBlue() > 450) { g.setColor(new Color(0, 0, 0, 160)); g.drawString(s, xx + 2, (int) y + 2); }
        g.setColor(c); g.drawString(s, xx, (int) y);
    }

    /** Readable world label: dark text on a white pill. */
    void tag(Graphics2D g, String s, double x, double y) {
        g.setFont(fTag);
        int w = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(255, 255, 255, 225)); g.fillRoundRect((int) x - w / 2 - 6, (int) y - 13, w + 12, 18, 10, 10);
        g.setColor(OUT); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect((int) x - w / 2 - 6, (int) y - 13, w + 12, 18, 10, 10);
        g.drawString(s, (int) x - w / 2, (int) y);
    }

    /** Big, high-contrast hint box. */
    void hintBox(Graphics2D g, String s, double cx, double y, Color border) {
        g.setFont(fHint);
        int w = g.getFontMetrics().stringWidth(s) + 36;
        cx = Math.max(10 + w / 2.0, Math.min(W - 10 - w / 2.0, cx));
        g.setColor(new Color(20, 22, 45, 240)); g.fillRoundRect((int) (cx - w / 2.0), (int) y, w, 36, 16, 16);
        g.setColor(border); g.setStroke(new BasicStroke(3)); g.drawRoundRect((int) (cx - w / 2.0), (int) y, w, 36, 16, 16);
        g.setColor(Color.WHITE); g.drawString(s, (int) (cx - w / 2.0 + 18), (int) y + 24);
    }

    void drawStore(Graphics2D g) {
        // floor
        for (int x = 40; x < 1240; x += 40)
            for (int y = 40; y < 690; y += 40) {
                boolean a = ((x / 40) + (y / 40)) % 2 == 0;
                g.setColor(a ? new Color(236, 232, 215) : new Color(222, 217, 198));
                g.fillRect(x, y, 40, 40);
            }
        // back room floor
        g.setColor(new Color(150, 156, 170)); g.fillRect(40, 40, 276, 206);
        g.setColor(new Color(130, 136, 150));
        for (int x = 40; x < 316; x += 46) g.drawLine(x, 40, x, 246);
        // wall frame
        g.setColor(OUT); g.setStroke(new BasicStroke(3));
        for (Rectangle2D.Double w : walls) if (w.width > 400 && w.height == 20 || w.width == 20 || w.height == 10 || w.width == 10) {
            g.setColor(new Color(110, 100, 130)); g.fill(w); g.setColor(OUT); g.draw(w);
        }
        tag(g, "BACK ROOM", 250, 126);
        // door
        g.setColor(new Color(255, 230, 90)); g.fillRoundRect(520, 692, 130, 16, 8, 8);
        text(g, "OPEN 24 HRS", 585, 705, fXs, OUT, true);
        // rug
        double wob = rugDirt > 50 ? Math.sin(t * 2) * 2 : 0;
        block(g, rugR.x, rugR.y + wob, rugR.width, rugR.height, new Color(120, 70, 70), 10);
        g.setColor(new Color(60, 40, 30, (int) (rugDirt * 1.6)));
        for (int i = 0; i < rugDirt / 8; i++) g.fillOval((int) rugR.x + 8 + (i * 37) % 110, (int) rugR.y + 6 + (i * 13) % 24, 8, 6);
        // spills
        for (Spill s : spills) {
            double k = s.hp / 100;
            g.setColor(new Color(90, 170, 240, 170)); g.fill(new Ellipse2D.Double(s.x - 34 * k - 6, s.y - 20 * k - 4, 68 * k + 12, 40 * k + 8));
            g.setColor(new Color(255, 255, 255, 140)); g.fill(new Ellipse2D.Double(s.x - 14, s.y - 8, 14 * k, 6 * k));
            g.setColor(OUT); g.setStroke(new BasicStroke(2)); g.draw(new Ellipse2D.Double(s.x - 34 * k - 6, s.y - 20 * k - 4, 68 * k + 12, 40 * k + 8));
        }
        // cig wall
        block(g, 340, 44, 420, 74, new Color(70, 60, 90), 8);
        g.setColor(new Color(40, 35, 55)); g.fillRect(350, 54, 400, 14);
        for (int i = 0; i < 20; i++) {
            g.setColor(SHIRTS[i % SHIRTS.length]); g.fillRect(354 + i * 20, 56, 14, 10);
            g.setColor(SHIRTS[(i + 3) % SHIRTS.length]); g.fillRect(354 + i * 20, 90, 14, 10);
        }
        g.setColor(new Color(255, 255, 255, 40)); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{6, 6}, 0));
        g.draw(new Ellipse2D.Double(CLIMB_X - 24, CLIMB_Y - 12, 48, 24));
        // counter + register
        block(g, 470, 232, 230, 38, new Color(190, 120, 70), 10);
        g.setColor(new Color(255, 255, 255, 70)); g.fillRect(480, 238, 210, 4);
        drawRegister(g);
        // scratcher stand
        block(g, 640, 238, 48, 26, new Color(80, 200, 120), 6);
        text(g, "$5", 664, 256, fXs, Color.WHITE, true);
        // safe
        block(g, safeP.x - 38, safeP.y - 34, 76, 70, new Color(100, 110, 125), 8);
        g.setColor(new Color(230, 200, 60)); g.fillOval((int) safeP.x - 14, (int) safeP.y - 14, 28, 28);
        g.setColor(OUT); g.setStroke(new BasicStroke(3)); g.drawOval((int) safeP.x - 14, (int) safeP.y - 14, 28, 28);
        g.drawLine((int) safeP.x, (int) safeP.y - 10, (int) safeP.x, (int) safeP.y + 10);
        tag(g, "SAFE: drop / buy cash", safeP.x, safeP.y + 54);
        String sa = safeAlert();
        if (!sa.isEmpty()) {
            double pul = 4 * Math.sin(t * 6);
            g.setColor(new Color(255, 230, 70, 200)); g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{9, 6}, (float) (t * 25)));
            g.draw(new Ellipse2D.Double(safeP.x - 52 - pul, safeP.y - 46 - pul, 104 + 2 * pul, 92 + 2 * pul));
            tag(g, sa + " HERE", safeP.x, safeP.y - 58);
        }
        // box pile
        for (int i = 0; i < 3; i++) block(g, boxPile.x - 30 + i * 22, boxPile.y - 20 + (i % 2) * 14, 44, 36, new Color(200, 160, 100), 4);
        tag(g, "STOCK BOXES", boxPile.x, boxPile.y + 46);
        // soda machine
        block(g, sodaR.x, sodaR.y, sodaR.width, sodaR.height, new Color(220, 50, 60), 12);
        for (int i = 0; i < 3; i++) block(g, sodaR.x + 10 + i * 34, sodaR.y + 12, 28, 60, new Color(250, 250, 255), 4);
        g.setColor(new Color(60, 40, 20, (int) (soda * 2))); g.fillRoundRect((int) sodaR.x + 8, (int) sodaR.y + 84, 98, 22, 8, 8);
        text(g, "SODA", sodaR.getCenterX(), sodaR.y + 118, fSm, Color.WHITE, true);
        if (soda > 40) { g.setColor(new Color(120, 70, 30, 150)); for (int i = 0; i < soda / 15; i++) g.fillOval((int) sodaR.x + 12 + i * 14, (int) sodaR.getMaxY() + 2, 6, 10 + i % 3 * 3); }
        // shelves
        for (Shelf s : shelves) {
            block(g, s.r.x, s.r.y, s.r.width, s.r.height, new Color(150, 110, 70), 6);
            int slots = (int) (s.r.width / 22);
            for (int i = 0; i < slots; i++) {
                if (i >= s.stock * slots / 10.0) continue;
                Color c = SHIRTS[(i + s.name.length()) % SHIRTS.length];
                double jitter = (1 - s.face / 100) * ((i * 7) % 9 - 4);
                g.setColor(c); g.fill(new RoundRectangle2D.Double(s.r.x + 5 + i * 22 + jitter, s.r.y + 4 + Math.abs(jitter) * 0.6, 17, 20, 4, 4));
                g.setColor(OUT); g.setStroke(new BasicStroke(1.6f)); g.draw(new RoundRectangle2D.Double(s.r.x + 5 + i * 22 + jitter, s.r.y + 4 + Math.abs(jitter) * 0.6, 17, 20, 4, 4));
            }
            if (s.stock == 0) text(g, "EMPTY!", s.r.getCenterX(), s.r.getCenterY() + 5, fSm, new Color(255, 90, 90), true);
            tag(g, s.name, s.r.getCenterX(), s.r.y - 6);
        }
        // task highlight
        if (activeTask != null && stun <= 0) {
            g.setColor(new Color(255, 255, 120, 150)); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{7, 5}, (float) (t * 20)));
            g.draw(new Ellipse2D.Double(activeTask.x - 28, activeTask.y - 18, 56, 36));
        }
    }

    void drawRegister(Graphics2D g) {
        boolean open = panelOpen();
        block(g, REG_X - 36, 222, 72, 44, new Color(60, 70, 90), 8);
        g.setColor(new Color(110, 230, 170)); g.fillRoundRect((int) REG_X - 26, 228, 52, 18, 4, 4);
        text(g, "$" + drawerValue(), REG_X, 242, fXs, OUT, true);
        if (open) { g.setColor(new Color(60, 160, 80)); g.fillRoundRect((int) REG_X - 30, 252, 60, 10, 4, 4); }
    }

    void person(Graphics2D g, double x, double y, Color shirt, Color skin, double bob, int mood, int hat, double dir, boolean lift) {
        double b = Math.sin(bob) * 2;
        double yy = y - (lift ? 18 : 0);
        g.setColor(new Color(0, 0, 0, 60)); g.fillOval((int) x - 16, (int) y - 5, 32, 12);
        g.setColor(new Color(50, 60, 100)); g.fillRoundRect((int) x - 11, (int) (yy - 14 + b * 0.3), 9, 16, 4, 4); g.fillRoundRect((int) x + 2, (int) (yy - 14 - b * 0.3), 9, 16, 4, 4);
        g.setColor(OUT); g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect((int) x - 11, (int) (yy - 14 + b * 0.3), 9, 16, 4, 4); g.drawRoundRect((int) x + 2, (int) (yy - 14 - b * 0.3), 9, 16, 4, 4);
        g.setColor(skin); g.fillRoundRect((int) x - 22, (int) (yy - 40 + b), 8, 22, 4, 4); g.fillRoundRect((int) x + 14, (int) (yy - 40 - b), 8, 22, 4, 4);
        g.setColor(OUT); g.drawRoundRect((int) x - 22, (int) (yy - 40 + b), 8, 22, 4, 4); g.drawRoundRect((int) x + 14, (int) (yy - 40 - b), 8, 22, 4, 4);
        g.setColor(shirt); g.fillRoundRect((int) x - 15, (int) (yy - 42), 30, 32, 8, 8);
        g.setColor(OUT); g.drawRoundRect((int) x - 15, (int) (yy - 42), 30, 32, 8, 8);
        g.setColor(skin); g.fillRoundRect((int) x - 13, (int) (yy - 68 + b * 0.5), 26, 26, 8, 8);
        g.setColor(OUT); g.drawRoundRect((int) x - 13, (int) (yy - 68 + b * 0.5), 26, 26, 8, 8);
        int hy = (int) (yy - 68 + b * 0.5);
        g.setColor(OUT);
        g.fillOval((int) (x - 7 + dir * 2), hy + 8, 4, 6); g.fillOval((int) (x + 3 + dir * 2), hy + 8, 4, 6);
        if (mood == 2) { // angry
            g.setStroke(new BasicStroke(2.5f)); g.drawLine((int) x - 9, hy + 5, (int) x - 2, hy + 8); g.drawLine((int) x + 9, hy + 5, (int) x + 2, hy + 8);
            g.drawArc((int) x - 6, hy + 17, 12, 7, 0, 180);
        } else if (mood == 1) { g.drawArc((int) x - 6, hy + 14, 12, 8, 200, 140); }
        else g.drawLine((int) x - 4, hy + 19, (int) x + 4, hy + 19);
        // hats
        if (hat == 1) { g.setColor(new Color(30, 30, 40)); g.fillRoundRect((int) x - 11, hy - 16, 22, 18, 4, 4); g.fillRoundRect((int) x - 17, hy - 2, 34, 5, 3, 3); g.setColor(new Color(240, 200, 60)); g.fillRect((int) x - 11, hy - 6, 22, 4); }
        if (hat == 2) { g.setColor(new Color(210, 50, 50)); g.fillArc((int) x - 14, hy - 8, 28, 22, 0, 180); g.fillRoundRect((int) (x + (dir > 0 ? 6 : -20)), hy + 1, 14, 4, 2, 2); }
        if (hat == 3) { g.setColor(new Color(60, 190, 100)); g.fillRoundRect((int) x - 15, hy - 1, 30, 7, 3, 3); g.setColor(OUT); g.drawRoundRect((int) x - 15, hy - 1, 30, 7, 3, 3); }
        if (hat == 4) { g.setColor(new Color(240, 200, 40)); g.fillArc((int) x - 14, hy - 10, 28, 24, 0, 180); g.fillRect((int) x - 16, hy + 1, 32, 4); }
        if (hat == 6) { g.setColor(new Color(60, 110, 220)); g.fillArc((int) x - 14, hy - 8, 28, 22, 0, 180); g.fillRoundRect((int) (x + (dir > 0 ? 6 : -20)), hy + 1, 14, 4, 2, 2); g.setColor(Color.WHITE); g.fillOval((int) x - 3, hy - 3, 6, 6); }
        if (hat == 7) { g.setColor(new Color(255, 214, 160)); g.fillRoundRect((int) x - 9, hy + 7, 18, 9, 4, 4); g.setColor(OUT); g.fillOval((int) (x - 6 + dir * 2), hy + 9, 4, 5); g.fillOval((int) (x + 2 + dir * 2), hy + 9, 4, 5); g.setStroke(new BasicStroke(2.5f)); g.drawLine((int) x - 9, hy + 6, (int) x - 3, hy + 8); g.drawLine((int) x + 9, hy + 6, (int) x + 3, hy + 8); }
        if (hat == 5) { g.setColor(new Color(255, 80, 80)); g.fillRoundRect((int) x - 12, hy - 6, 24, 9, 6, 6); } // staff cap
    }

    void drawCustomer(Graphics2D g, Customer c) {
        int hat = c.type == T_BIG ? 1 : c.type == T_CIG ? 2 : c.type == T_SCRATCH ? 3 : c.type == T_ASK ? 4 : c.type == T_BEER ? 6 : c.type == T_ROB ? 7 : 0;
        int mood = c.angry || c.pat < 0.35 ? 2 : c.pat < 0.7 ? 0 : 1;
        person(g, c.x, c.y, c.shirt, c.skin, c.bob, mood, hat, c.dir, false);
        tag(g, c.name, c.x, c.y + 22);
        // patience bar for the waiting
        if ((c.state == S_QUEUE && c.need != N_SCRATCH) || c.state == S_ASK) {
            double w = 40;
            if (c.paused) text(g, "|| paused", c.x, c.y - 92, fXs, new Color(255, 255, 255), true);
            g.setColor(OUT); g.fillRoundRect((int) (c.x - w / 2 - 2), (int) c.y - 88, (int) w + 4, 10, 6, 6);
            g.setColor(c.pat > 0.5 ? new Color(90, 210, 110) : c.pat > 0.25 ? new Color(250, 190, 50) : new Color(240, 70, 70));
            g.fillRoundRect((int) (c.x - w / 2), (int) c.y - 86, (int) (w * Math.max(0, c.pat)), 6, 4, 4);
        }
        if (c.need == N_SCRATCH && c.state == S_QUEUE) {
            text(g, "scratch scratch scratch", c.x, c.y - 90, fXs, new Color(60, 190, 100), true);
            g.setColor(new Color(240, 230, 150)); g.fillRoundRect((int) c.x - 10, (int) c.y - 28, 20, 12, 3, 3);
            g.setColor(new Color(130, 130, 130)); g.fillOval((int) (c.x - 6 + Math.sin(t * 25) * 5), (int) c.y - 25, 5, 5);
        }
    }

    void drawPlayer(Graphics2D g) {
        boolean moving = (up || down || left || right) && stun <= 0 && !climbing;
        person(g, px, py, new Color(240, 70, 70), SKINS[0], moving ? t * 14 : 0, stun > 0 ? 2 : 1, 5, facingDir, false);
        // red employee vest
        g.setColor(new Color(255, 255, 255, 200)); g.fillRect((int) px - 6, (int) (py - 36), 12, 3);
        if (carry == C_BOX) block(g, px - 18, py - 58, 36, 28, new Color(200, 160, 100), 4);
        if (carry == C_PACK) { block(g, px - 9, py - 62, 18, 24, new Color(235, 60, 60), 3); text(g, "CIGS", px, py - 70, fXs, Color.WHITE, true); }
        if (beatT > 0) {
            AffineTransform old = g.getTransform();
            double swing = Math.sin(beatSwing / 0.32 * Math.PI);
            double ang = facingDir * (-1.2 + 2.2 * swing);
            g.translate(px + 14 * facingDir, py - 40);
            g.rotate(ang);
            g.setColor(new Color(150, 100, 50)); g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(0, 0, 0, -58);
            g.setColor(OUT); g.setStroke(new BasicStroke(2f)); g.drawRoundRect(-2, -62, 0, 0, 0, 0);
            g.setColor(new Color(225, 225, 235)); g.fillRoundRect(-14, -78, 28, 22, 8, 8);
            g.setColor(OUT); g.drawRoundRect(-14, -78, 28, 22, 8, 8);
            g.setTransform(old);
        }
        if (stun > 0) text(g, "!?!", px, py - 84, fMid, new Color(255, 80, 80), true);
        if (heldTask != null && heldTask.ui != null) {
            double w = 54, k = Math.max(0, Math.min(1, heldTask.ui.getAsDouble()));
            g.setColor(OUT); g.fillRoundRect((int) (px - w / 2 - 3), (int) py - 96, (int) w + 6, 14, 8, 8);
            g.setColor(new Color(255, 215, 70)); g.fillRoundRect((int) (px - w / 2), (int) py - 93, (int) (w * k), 8, 6, 6);
        }
    }

    List<String> wrap(Graphics2D g, String s, int maxW) {
        List<String> out = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            if (g.getFontMetrics().stringWidth(cur + " " + w) > maxW && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        out.add(cur.toString());
        return out;
    }

    void drawBubble(Graphics2D g, Customer c) {
        if (c.sayT <= 0 || c.say.isEmpty()) return;
        g.setFont(fSm);
        List<String> ls = wrap(g, c.say, 170);
        int w = 0; for (String l : ls) w = Math.max(w, g.getFontMetrics().stringWidth(l));
        int bh = ls.size() * 17 + 12, bw = w + 18;
        int bx = (int) Math.max(8, Math.min(W - bw - 8, c.x - bw / 2.0)), by = (int) c.y - 100 - bh;
        g.setColor(new Color(0, 0, 0, 50)); g.fillRoundRect(bx + 3, by + 4, bw, bh, 14, 14);
        g.setColor(Color.WHITE); g.fillRoundRect(bx, by, bw, bh, 14, 14);
        Polygon tail = new Polygon(new int[]{(int) c.x - 6, (int) c.x + 6, (int) c.x}, new int[]{by + bh - 1, by + bh - 1, by + bh + 9}, 3);
        g.fill(tail);
        g.setColor(OUT); g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect(bx, by, bw, bh, 14, 14);
        g.setColor(c.angry ? new Color(200, 40, 40) : OUT);
        for (int i = 0; i < ls.size(); i++) g.drawString(ls.get(i), bx + 9, by + 17 + i * 17);
    }

    // ---------- HUD ----------
    void meter(Graphics2D g, double x, double y, double w, double h, double frac, Color c, String label) {
        g.setColor(OUT); g.fillRoundRect((int) x, (int) y, (int) w, (int) h, 12, 12);
        g.setColor(c); g.fillRoundRect((int) x + 2, (int) y + 2, (int) ((w - 4) * Math.max(0.02, Math.min(1, frac))), (int) h - 4, 9, 9);
        g.setColor(new Color(255, 255, 255, 70)); g.fillRoundRect((int) x + 4, (int) y + 3, (int) ((w - 8) * Math.max(0.02, Math.min(1, frac))), 4, 4, 4);
        text(g, label, x + w / 2, y + h - 6, fSm, Color.WHITE, true);
    }

    void drawHud(Graphics2D g) {
        // ---------- top-left card: health / rage / drawer ----------
        g.setColor(new Color(20, 22, 45, 225)); g.fillRoundRect(10, 6, 336, 96, 18, 18);
        g.setColor(OUT); g.setStroke(new BasicStroke(3)); g.drawRoundRect(10, 6, 336, 96, 18, 18);
        double hp = Math.max(0, healthShown) / 100;
        Color hc = hp > 0.55 ? new Color(90, 215, 110) : hp > 0.28 ? new Color(250, 190, 50) : new Color(240, 70, 70);
        if (hp < 0.28 && (int) (t * 4) % 2 == 0) hc = new Color(255, 120, 120);
        meter(g, 18, 12, 320, 24, hp, hc, "HEALTH  " + (int) health);
        double rg = Math.max(0, Math.min(1, rage / 100));
        Color rc = rg < 0.4 ? new Color(120, 200, 240) : rg < 0.7 ? new Color(250, 170, 50) : new Color(240, 60, 50);
        if (rg > 0.82 && (int) (t * 5) % 2 == 0) rc = new Color(255, 150, 120);
        String rw = rg < 0.25 ? "CALM" : rg < 0.5 ? "ANNOYED" : rg < 0.75 ? "FUMING" : "ABOUT TO SNAP";
        if (snapT > 0.3) rw = "SNAPPING IN " + (int) Math.ceil(SNAP_LIMIT - snapT) + "s!";
        meter(g, 18, 40, 320, 24, rg, rc, "RAGE  " + rw);
        int dv = drawerValue();
        text(g, "DRAWER $" + dv, 20, 91, fSm, dv > 350 ? new Color(255, 120, 110) : Color.WHITE, false);
        String cs = carry == C_BOX ? "carrying BOX (Q: drop)" : carry == C_PACK ? "carrying CIGS - take to register" : "";
        boolean nearCash = atRegister() || dist(px, py, safeP.x, safeP.y) < 130;
        if (!cs.isEmpty()) text(g, cs, 150, 91, fSm, new Color(130, 230, 170), false);
        else if (nearCash) text(g, "1s:" + drawer[0] + "  5s:" + drawer[1] + "  10s:" + drawer[2] + "  20s:" + drawer[3] + "  big:$" + bigPile(), 150, 91, fXs, new Color(200, 210, 230), false);

        // ---------- bottom-right card: time, night, phase, traffic ----------
        int mins = (int) (prog() * 480);
        int hh = (22 + mins / 60) % 24, mm = mins % 60;
        String ck = String.format("%d:%02d %s", hh % 12 == 0 ? 12 : hh % 12, mm, hh >= 12 ? "PM" : "AM");
        double rk = effRush();
        String rl = rk < 0.2 ? "quiet" : rk < 0.5 ? "busy" : rk < 0.8 ? "rush" : "mob scene";
        double cx0 = W - 276, cy0 = H - 34 - 98;
        g.setColor(new Color(20, 22, 45, 225)); g.fillRoundRect((int) cx0, (int) cy0, 266, 98, 18, 18);
        g.setColor(OUT); g.setStroke(new BasicStroke(3)); g.drawRoundRect((int) cx0, (int) cy0, 266, 98, 18, 18);
        text(g, ck, cx0 + 14, cy0 + 34, new Font("Arial Rounded MT Bold", Font.BOLD, 30), Color.WHITE, false);
        text(g, "NIGHT " + night, cx0 + 252 - g.getFontMetrics(fSm).stringWidth("NIGHT " + night), cy0 + 30, fSm, new Color(255, 225, 60), false);
        text(g, phaseName, cx0 + 14, cy0 + 56, fSm, new Color(200, 210, 230), false);
        meter(g, cx0 + 10, cy0 + 64, 246, 24, rk, rk < 0.35 ? new Color(110, 200, 240) : rk < 0.7 ? new Color(250, 190, 50) : new Color(240, 90, 70), "TRAFFIC  " + rl);

        // ---------- top right: tips + combo ----------
        g.setColor(new Color(20, 22, 45, 225)); g.fillRoundRect(1050, 6, 216, 32, 16, 16);
        text(g, "TIPS $" + tips, 1066, 28, fSm, new Color(255, 225, 60), false);
        text(g, "COMBO x" + combo, 1160, 28, fSm, combo >= 3 ? new Color(255, 160, 60) : Color.WHITE, false);

        // ---------- ONE alert at a time (most urgent wins) ----------
        String alert = null; Color ac = new Color(255, 70, 70);
        if (robberPresent()) alert = "ROBBER! Drop cash at the safe NOW!";
        else if (robT > 0.5) alert = "ROBBERY RISK - drop cash in " + (int) Math.ceil(robLimit() - robT) + "s";
        else if (!lowBills().isEmpty()) { alert = "Low on " + lowBills() + " - BUY CASH at the safe"; ac = new Color(255, 130, 90); }
        else if (drawerValue() > 300) { alert = "Drawer getting full - DROP CASH at the safe"; ac = new Color(255, 215, 70); }
        else if (cigWaiting() != null && carry == C_NONE) { alert = "Cigs coming - climb up for them now to get ahead"; ac = new Color(130, 230, 170); }
        if (alert != null) hintBox(g, alert, W / 2.0, 8, ac);

        // ---------- toast (top center, wrapped) ----------
        if (toastT > 0) {
            g.setFont(fHint);
            List<String> tl = wrap(g, toast, 560);
            int bw = 0; for (String l : tl) bw = Math.max(bw, g.getFontMetrics().stringWidth(l));
            int bh = tl.size() * 24 + 14;
            block(g, W / 2.0 - bw / 2.0 - 18, 72, bw + 36, bh, new Color(200, 50, 60), 16);
            for (int k = 0; k < tl.size(); k++) text(g, tl.get(k), W / 2.0, 72 + 25 + k * 24, fHint, Color.WHITE, true);
        }

        // ---------- bottom: E prompt, F pill ----------
        if (activeTask != null && stun <= 0 && !panelOpen()) {
            String p = (activeTask.hold ? "HOLD [E]  " : "TAP [E]  ") + activeTask.label;
            g.setFont(fMid); int w = g.getFontMetrics().stringWidth(p) + 44;
            block(g, W / 2.0 - w / 2.0, H - 76, w, 44, new Color(255, 215, 70), 16);
            text(g, p, W / 2.0, H - 46, fMid, OUT, true);
        }
        if (!queue.isEmpty()) {
            double minPat = 1; for (Customer c : queue) if (c.need != N_SCRATCH) minPat = Math.min(minPat, c.pat);
            boolean rdy = reassureCd <= 0;
            if (!rdy || minPat < 0.8)
                hintBox(g, rdy ? "[F]  calm the line" : "[F]  ready in " + (int) Math.ceil(reassureCd) + "s", W - 150, H - 34 - 98 - 46, rdy ? new Color(130, 230, 170) : new Color(120, 120, 140));
        }
        drawSafePanel(g);
        drawCoworkerHint(g);
        g.setColor(new Color(20, 22, 45, 245)); g.fillRect(0, H - 26, W, 26);
        text(g, "WASD move   E interact (hold = chores)   Q put down   F calm line   C co-worker   H help   M sound" + (Snd.muted ? " (muted)" : ""),
                W / 2.0, H - 8, fSm, Color.WHITE, true);
    }

    String safeAlert() {
        if (robberPresent() && drawerExcess() > 0) return "DROP CASH NOW";
        if (!lowBills().isEmpty() || (panelOpen() && front() != null && !canMake(front().bill - front().total - givenSum()))) return "BUY CASH";
        if (drawerValue() > 300) return "DROP CASH";
        return "";
    }
    String lowBills() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) if (drawer[i] < 3 && safe[i] > 0) b.append(b.length() > 0 ? ", " : "").append("$").append(DEN[i]).append("s");
        return b.toString();
    }

    void drawSafePanel(Graphics2D g) {
        if (dist(px, py, safeP.x, safeP.y) > 130) return;
        double x = 44, y = 268;
        g.setColor(new Color(20, 22, 45, 235)); g.fillRoundRect((int) x, (int) y, 372, 92, 16, 16);
        g.setColor(new Color(255, 230, 70)); g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect((int) x, (int) y, 372, 92, 16, 16);
        text(g, "SAFE:  1s x" + safe[0] + "   5s x" + safe[1] + "   10s x" + safe[2] + "   20s x" + safe[3] + "   big $" + safeBig, x + 14, y + 24, fXs, new Color(200, 210, 230), false);
        text(g, "DROP CASH = extra/big bills INTO the safe", x + 14, y + 51, fSm, new Color(130, 230, 170), false);
        text(g, "BUY CASH  = small bills OUT of the safe", x + 14, y + 76, fSm, new Color(130, 200, 255), false);
    }

    void drawPanel(Graphics2D g) {
        Customer c = front();
        double x0 = W / 2.0 - 250, y0 = H - 310;
        block(g, x0, y0, 500, 260, new Color(45, 55, 85), 20);
        int due = c.bill - c.total;
        text(g, c.name + " paid with a $" + c.bill + " for $" + c.total, W / 2.0, y0 + 30, fMid, Color.WHITE, true);
        text(g, "CHANGE DUE:  $" + due, W / 2.0 - 120, y0 + 62, fMid, new Color(255, 225, 60), true);
        text(g, "GIVEN:  $" + givenSum(), W / 2.0 + 110, y0 + 62, fMid, givenSum() == due ? new Color(120, 240, 150) : Color.WHITE, true);
        String[] lab = {"$1", "$5", "$10", "$20"};
        for (int i = 0; i < 4; i++) {
            double bx = x0 + 20 + i * 100, by = y0 + 80;
            boolean out = given[i] >= drawer[i];
            panelBtn[i] = new Rectangle2D.Double(bx, by, 90, 54);
            block(g, bx, by, 90, 54, out ? new Color(80, 80, 92) : new Color(24, 120, 66), 12);
            text(g, lab[i] + "  [" + (i + 1) + "]", bx + 45, by + 24, fSm, Color.WHITE, true);
            text(g, "x" + given[i] + "  (left " + (drawer[i] - given[i]) + ")", bx + 45, by + 43, fXs, Color.WHITE, true);
        }
        panelBtn[4] = new Rectangle2D.Double(x0 + 420, y0 + 80, 64, 54);
        block(g, x0 + 420, y0 + 80, 64, 54, new Color(176, 72, 22), 12);
        text(g, "CLEAR", x0 + 452, y0 + 112, fXs, Color.WHITE, true);
        // big bills customers paid with: yours to use as change until you drop them in the safe
        for (int k = 0; k < 2; k++) {
            double bx = x0 + 20 + k * 150, by = y0 + 144;
            boolean out = bigGiven[k] >= big[k];
            panelBtn[6 + k] = new Rectangle2D.Double(bx, by, 140, 46);
            block(g, bx, by, 140, 46, out ? new Color(80, 80, 92) : new Color(140, 100, 10), 12);
            text(g, "$" + BIGDEN[k] + "  [" + (5 + k) + "]", bx + 70, by + 20, fSm, Color.WHITE, true);
            text(g, "x" + bigGiven[k] + "  (left " + (big[k] - bigGiven[k]) + ")", bx + 70, by + 38, fXs, Color.WHITE, true);
        }
        text(g, "big bills stay here until", x0 + 400, y0 + 163, fXs, new Color(200, 205, 230), true);
        text(g, "you DROP CASH", x0 + 400, y0 + 180, fXs, new Color(200, 205, 230), true);
        panelBtn[5] = new Rectangle2D.Double(x0 + 20, y0 + 202, 460, 40);
        block(g, x0 + 20, y0 + 202, 460, 40, givenSum() == due ? new Color(24, 120, 66) : new Color(66, 76, 112), 12);
        text(g, "DONE  [ENTER]", W / 2.0, y0 + 229, fMid, Color.WHITE, true);
        if (!canMake(due - givenSum()))
            text(g, "NOT ENOUGH BILLS! Run to the safe (grab change)!", W / 2.0, y0 - 8, fSm, new Color(255, 110, 110), true);
    }

    void drawTitle(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(255, 120, 90), 0, H, new Color(120, 60, 200)));
        g.fillRect(0, 0, W, H);
        for (int i = 0; i < 12; i++) { g.setColor(new Color(255, 255, 255, 20)); g.fillRect(i * 110, 0, 55, H); }
        text(g, "SPEED-E-MART", W / 2.0, 140, new Font("Arial Rounded MT Bold", Font.BOLD, 96), new Color(255, 230, 70), true);
        text(g, "NIGHT SHIFT", W / 2.0, 200, fBig, Color.WHITE, true);
        String[] l = {
                "It's 10 PM. The coffee is cold. Customers are not.",
                "",
                "WASD move   SHIFT sprint   E interact (hold for chores)   Q put things down",
                "Ring people up, make exact change (keys 1-6 or click), drop cash in the safe.",
                "Keep STORE HEALTH up: mop spills, shake the rug, clean the soda machine,",
                "stock shelves (grab a box from the back room) and front-face the shelves.",
                "Make customers happy FAST for tips and combos.",
                "",
                "Cigarettes are on the TOP shelf. Yes, you have to climb up for them. Every. Time.",
                "Big bills, scratcher marathons, and people who need you RIGHT NOW",
                "will interrupt whatever you're doing. Survive until 6 AM."};
        for (int i = 0; i < l.length; i++) text(g, l[i], W / 2.0, 280 + i * 30, fSm, Color.WHITE, true);
        if ((int) (System.currentTimeMillis() / 500) % 2 == 0) text(g, "PRESS ENTER TO CLOCK IN", W / 2.0, 640, fMid, new Color(255, 230, 70), true);
        text(g, "H = full help & controls     M = sound on/off", W / 2.0, 676, fSm, Color.WHITE, true);
    }

    /** End-of-shift bonuses: {label, points, earned}. Only paid out when you survive to 6 AM. */
    List<Object[]> bonuses() {
        List<Object[]> b = new ArrayList<>();
        b.add(new Object[]{"No walkouts", 300, angryCt == 0});
        b.add(new Object[]{"Never snapped", 200, snaps == 0});
        b.add(new Object[]{"Never robbed", 150, robberies == 0});
        b.add(new Object[]{"Combo x5 or better", 150, bestCombo >= 5});
        b.add(new Object[]{"Spotless store (health 80+)", 200, health >= 80});
        b.add(new Object[]{"Flew solo (no co-worker)", 250, !cwUsed});
        return b;
    }
    int bonusTotal() { int t = 0; for (Object[] r : bonuses()) if ((Boolean) r[2]) t += (Integer) r[1]; return t; }

    /** This shift's score (bonuses only count if you made it to 6 AM). */
    int shiftScore(boolean win) {
        int bonus = win ? bonusTotal() : 0;
        int score = tips * 10 + happy * 25 + (int) health * (win ? 5 : 0) - angryCt * 20 + (win ? 500 : 0) + bonus;
        return Math.max(0, score - robbedTotal);
    }

    void drawEnd(Graphics2D g, boolean win) {
        g.setColor(new Color(0, 0, 0, 170)); g.fillRect(0, 0, W, H);
        blockHdr(g, W / 2.0 - 280, 30, 560, 660, new Color(50, 60, 95), 28, 120);
        text(g, win ? "SHIFT OVER! 6:00 AM" : "YOU'RE FIRED", W / 2.0, 92, fBig, win ? new Color(120, 240, 150) : new Color(255, 100, 100), true);
        text(g, win ? "You survived night " + night + ". The next one will be harder." : "The store fell apart. Manager is NOT happy.", W / 2.0, 128, fSm, Color.WHITE, true);
        String[] rows = {"Tips earned: $" + tips, "Happy customers: " + happy, "Angry walkouts: " + angryCt,
                "Best combo: x" + bestCombo, "Customers served: " + served, "Robbed: " + robberies + "x  ($" + robbedTotal + " lost)"};
        for (int i = 0; i < rows.length; i++) text(g, rows[i], W / 2.0, 176 + i * 26, fSm, Color.WHITE, true);
        // bonuses
        text(g, "BONUSES", W / 2.0, 354, fMid, new Color(255, 225, 60), true);
        if (win) {
            List<Object[]> bl = bonuses();
            for (int i = 0; i < bl.size(); i++) {
                Object[] r = bl.get(i); boolean got = (Boolean) r[2]; double yy = 384 + i * 26;
                Color c = got ? new Color(120, 240, 150) : new Color(150, 155, 180);
                text(g, (got ? "+ " : "- ") + r[0], W / 2.0 - 230, yy, fSm, c, false);
                text(g, got ? "+" + r[1] : "0", W / 2.0 + 190, yy, fSm, c, false);
            }
        } else text(g, "No bonuses when you get fired.", W / 2.0, 390, fSm, new Color(150, 155, 180), true);
        text(g, "SHIFT SCORE: " + lastShiftScore, W / 2.0, 548, fMid, new Color(255, 225, 60), true);
        text(g, win ? "RUNNING TOTAL: " + runScore : "RUNNING TOTAL: " + runScore + "  (this shift doesn't count)", W / 2.0, 578, fMid, win ? new Color(120, 240, 150) : new Color(190, 195, 215), true);
        text(g, win ? "Clock in for night " + (night + 1) + "?" : "Beg for your job back (retry night " + night + ")?", W / 2.0, 608, fSm, new Color(255, 225, 60), true);
        double by = 626, bw = 220, bh = 50;
        endBtn[0] = new Rectangle2D.Double(W / 2.0 - bw - 14, by, bw, bh);
        endBtn[1] = new Rectangle2D.Double(W / 2.0 + 14, by, bw, bh);
        block(g, endBtn[0].x, by, bw, bh, new Color(24, 120, 66), 14);
        text(g, "OKAY  [ENTER]", endBtn[0].getCenterX(), by + 32, fMid, Color.WHITE, true);
        block(g, endBtn[1].x, by, bw, bh, new Color(165, 50, 50), 14);
        text(g, "QUIT  [ESC]", endBtn[1].getCenterX(), by + 32, fMid, Color.WHITE, true);
    }

    // =====================================================================
    //  SOUND -- all effects are synthesized at startup, no audio files
    // =====================================================================
    static class Snd {
        static boolean muted = false, ok = true;
        static final float RATE = 22050f;
        static final Map<String, byte[]> BANK = new HashMap<>();
        static final Random R = new Random(3);
        static long lastNs = 0;
        static java.util.concurrent.ExecutorService pool;

        static byte[] gen(double secs, DoubleUnaryOperator f) {
            int n = (int) (secs * RATE);
            byte[] b = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                double t = i / (double) RATE;
                double v = Math.max(-1, Math.min(1, f.applyAsDouble(t)));
                double edge = Math.min(1, Math.min(i, n - i) / 200.0);   // de-click
                short s = (short) (v * edge * 26000);
                b[i * 2] = (byte) (s & 0xff); b[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
            }
            return b;
        }
        static double sine(double f, double t) { return Math.sin(2 * Math.PI * f * t); }
        static double sq(double f, double t) { return sine(f, t) >= 0 ? 1 : -1; }
        static double saw(double f, double t) { return 2 * ((f * t) % 1.0) - 1; }
        static double env(double t, double d) { return Math.exp(-t / d); }

        static {
            BANK.put("bell", gen(0.7, t -> 0.5 * (sine(988, t) + 0.6 * sine(1480, t)) * env(t, 0.18)
                    + (t > 0.18 ? 0.5 * (sine(784, t) + 0.6 * sine(1176, t)) * env(t - 0.18, 0.2) : 0)));
            BANK.put("beep", gen(0.07, t -> 0.35 * sq(1250, t)));
            BANK.put("click", gen(0.04, t -> 0.4 * sq(700, t) * env(t, 0.02)));
            BANK.put("kaching", gen(0.55, t -> (t < 0.05 ? (R.nextDouble() - 0.5) * 0.8 : 0)
                    + 0.35 * (sine(1568, t) + sine(2093, t)) * env(t, 0.14)
                    + (t > 0.09 ? 0.35 * (sine(2637, t) + 0.5 * sine(3136, t)) * env(t - 0.09, 0.2) : 0)));
            BANK.put("buzz", gen(0.4, t -> 0.5 * saw(95 - 25 * t, t) * env(t, 0.5) + 0.2 * sq(140, t) * env(t, 0.3)));
            BANK.put("alarm", gen(0.36, t -> 0.45 * sq(t % 0.18 < 0.09 ? 880 : 620, t) * (1 - t)));
            BANK.put("pick", gen(0.1, t -> 0.4 * sine(380 + 3500 * t, t) * env(t, 0.06)));
            BANK.put("thud", gen(0.2, t -> 0.8 * sine(95 - 150 * t, t) * env(t, 0.07)));
            BANK.put("clink", gen(0.3, t -> 0.35 * (sine(2400, t) + sine(3300, t) * 0.6) * env(t, 0.05)
                    + (t > 0.07 ? 0.3 * sine(2900, t) * env(t - 0.07, 0.05) : 0)));
            BANK.put("swish", gen(0.16, t -> (R.nextDouble() - 0.5) * 0.55 * Math.sin(Math.PI * t / 0.16)));
            BANK.put("creak", gen(0.16, t -> 0.25 * saw(210 + 160 * Math.sin(t * 40), t) * Math.sin(Math.PI * t / 0.16)));
            BANK.put("win", gen(1.1, t -> {
                double[] n = {523, 659, 784, 1047};
                int i = Math.min(3, (int) (t / 0.16));
                return 0.4 * sq(n[i], t) * env(t - i * 0.16, 0.5) * (i == 3 ? 1 : 0.8);
            }));
            BANK.put("lose", gen(1.0, t -> {
                double[] n = {392, 330, 262, 196};
                int i = Math.min(3, (int) (t / 0.22));
                return 0.4 * saw(n[i], t) * env(t - i * 0.22, 0.5);
            }));
        }

        static void play(String name) {
            if (muted || !ok) return;
            if (name.equals("swish") || name.equals("creak") || name.equals("click")) {
                // short repeated sounds: never spam
                long now = System.nanoTime();
                if (now - lastNs < 60_000_000L) return;
                lastNs = now;
            }
            byte[] data = BANK.get(name);
            if (data == null) return;
            if (pool == null) pool = java.util.concurrent.Executors.newFixedThreadPool(2, r -> { Thread th = new Thread(r); th.setDaemon(true); return th; });
            pool.submit(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                    Clip c = AudioSystem.getClip();
                    c.open(fmt, data, 0, data.length);
                    c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) c.close(); });
                    c.start();
                } catch (Throwable ex) { ok = false; }   // no audio device: silently carry on
            });
        }
    }

    // =====================================================================
    //  HEADLESS SELF-TEST (bot plays a full shift, saves screenshots)
    // =====================================================================
    static void selfTest(String prefix) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Snd.muted = true;
        Game g = new Game();
        g.newGame();
        g.help = false;
        Random r = new Random(7);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        double dt = 1 / 30.0; int frames = 0;
        double tx = g.px, ty = g.py; int exceptions = 0;
        while (g.scene == Scene.PLAY && frames < 30 * 600) {
            // dumb bot: wander to random task locations, hold E, press number keys
            if (frames % 60 == 0) {
                List<Task> L = g.buildTasks();
                if (!L.isEmpty() && r.nextInt(10) > 1) { Task k = L.get(r.nextInt(L.size())); tx = k.x; ty = k.y; }
                else { tx = 80 + r.nextInt(1100); ty = 100 + r.nextInt(550); }
                if (g.carry != C_NONE && r.nextInt(5) == 0) g.qPressed = true;
            }
            g.left = g.px > tx + 8; g.right = g.px < tx - 8; g.up = g.py > ty + 8; g.down = g.py < ty - 8;
            g.eDown = true; g.ePressed = frames % 15 == 0;
            if (g.panelOpen() && frames % 10 == 0) {
                Customer c = g.front(); int due = c.bill - c.total - g.givenSum();
                for (int i = 3; i >= 0; i--) if (DEN[i] <= due) { g.addGiven(i); break; }
                if (g.givenSum() == c.bill - c.total) g.submitChange();
            }
            try { g.step(dt); } catch (Exception e) { e.printStackTrace(); exceptions++; break; }
            frames++;
            if (frames == 30 * 45 || frames == 30 * 200) {
                Graphics2D gg = img.createGraphics(); g.draw(gg); gg.dispose();
                javax.imageio.ImageIO.write(img, "png", new java.io.File(prefix + "_" + (frames / 30) + ".png"));
            }
        }
        Graphics2D gg = img.createGraphics(); g.draw(gg); gg.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(prefix + "_end.png"));
        System.out.printf("scene=%s t=%.0f health=%.1f tips=%d happy=%d angry=%d served=%d exceptions=%d%n",
                g.scene, g.t, g.health, g.tips, g.happy, g.angryCt, g.served, exceptions);
    }
}
