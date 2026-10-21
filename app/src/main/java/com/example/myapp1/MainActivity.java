package com.example.myapp1; // تأكد من مطابقة اسم الحزمة لمشروعك

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    // --- Game Engine ---
    class GameView extends SurfaceView implements Runnable {

        private Thread gameThread = null;
        private SurfaceHolder surfaceHolder;
        private volatile boolean isPlaying;

        private Paint paint;
        private int screenWidth = 0, screenHeight = 0;
        private boolean isInitialized = false;

        // عناصر اللعبة
        private Player player;
        private ArrayList<Bullet> bullets;
        private ArrayList<Zombie> zombies;
        private ArrayList<Explosion> explosions;
        private ArrayList<FireArea> fireAreas;
        private ArrayList<ThrowableItem> throwables;

        // التحكم
        private float joystickBaseX, joystickBaseY;
        private float joystickTouchX, joystickTouchY;
        private float joystickRadius = 120;
        private boolean isJoystickActive = false;
        private int joystickPointerId = -1;

        // الأزرار
        private RectF shootButton;
        private RectF grenadeButton;
        private RectF molotovButton;
        private RectF shopButton;

        private boolean isShootingPressed = false;
        private long lastShootTime = 0;

        // المتجر وحالة اللعبة
        private boolean isShopOpen = false;
        private RectF buyHealthBtn, buyGrenadeBtn, buyMolotovBtn, closeShopBtn;

        private int score = 0;
        private int coins = 0;
        private boolean isGameOver = false;
        private Random random;
        private long lastZombieSpawnTime = 0;

        public GameView(Context context) {
            super(context);
            surfaceHolder = getHolder();
            paint = new Paint();
            bullets = new ArrayList<>();
            zombies = new ArrayList<>();
            explosions = new ArrayList<>();
            fireAreas = new ArrayList<>();
            throwables = new ArrayList<>();
            random = new Random();

            shootButton = new RectF();
            grenadeButton = new RectF();
            molotovButton = new RectF();
            shopButton = new RectF();

            buyHealthBtn = new RectF();
            buyGrenadeBtn = new RectF();
            buyMolotovBtn = new RectF();
            closeShopBtn = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenWidth = w;
            screenHeight = h;

            if (w > 0 && h > 0) {
                // مواقع عناصر التحكم
                joystickBaseX = 220;
                joystickBaseY = screenHeight - 220;
                joystickTouchX = joystickBaseX;
                joystickTouchY = joystickBaseY;

                shootButton.set(screenWidth - 220, screenHeight - 220, screenWidth - 60, screenHeight - 60);
                grenadeButton.set(screenWidth - 380, screenHeight - 160, screenWidth - 250, screenHeight - 60);
                molotovButton.set(screenWidth - 530, screenHeight - 160, screenWidth - 400, screenHeight - 60);

                shopButton.set(screenWidth - 180, 30, screenWidth - 30, 110);

                // نافذة المتجر
                float cX = screenWidth / 2f;
                float cY = screenHeight / 2f;
                buyHealthBtn.set(cX - 250, cY - 100, cX + 250, cY - 20);
                buyGrenadeBtn.set(cX - 250, cY + 0, cX + 250, cY + 80);
                buyMolotovBtn.set(cX - 250, cY + 100, cX + 250, cY + 180);
                closeShopBtn.set(cX - 100, cY + 210, cX + 100, cY + 270);

                resetGame();
                isInitialized = true;
            }
        }

        private void resetGame() {
            player = new Player(screenWidth / 2f, screenHeight / 2f);
            bullets.clear();
            zombies.clear();
            explosions.clear();
            fireAreas.clear();
            throwables.clear();
            score = 0;
            coins = 0;
            isGameOver = false;
            isShopOpen = false;
        }

        @Override
        public void run() {
            while (isPlaying) {
                if (isInitialized) {
                    update();
                    draw();
                }
                controlFPS();
            }
        }

        private void update() {
            if (isGameOver || isShopOpen) return;

            try {
                // 1. الحركة
                if (isJoystickActive) {
                    float dx = joystickTouchX - joystickBaseX;
                    float dy = joystickTouchY - joystickBaseY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (dist > 0) {
                        player.x += (dx / dist) * player.speed;
                        player.y += (dy / dist) * player.speed;

                        player.x = Math.max(player.radius, Math.min(screenWidth - player.radius, player.x));
                        player.y = Math.max(player.radius, Math.min(screenHeight - player.radius, player.y));

                        player.angle = (float) Math.atan2(dy, dx);
                    }
                }

                // 2. الإطلاق
                long currentTime = System.currentTimeMillis();
                if (isShootingPressed && currentTime - lastShootTime > 180) {
                    bullets.add(new Bullet(player.x, player.y, player.angle));
                    lastShootTime = currentTime;
                }

                // 3. تحديث الرصاص
                Iterator<Bullet> bulletIt = bullets.iterator();
                while (bulletIt.hasNext()) {
                    Bullet b = bulletIt.next();
                    b.update();
                    if (b.x < 0 || b.x > screenWidth || b.y < 0 || b.y > screenHeight) {
                        bulletIt.remove();
                    }
                }

                // 4. تحديث المقذوفات
                Iterator<ThrowableItem> itemIt = throwables.iterator();
                while (itemIt.hasNext()) {
                    ThrowableItem item = itemIt.next();
                    item.update();
                    if (item.isExploded()) {
                        if (item.type == ThrowableItem.TYPE_GRENADE) {
                            explosions.add(new Explosion(item.targetX, item.targetY, 160, 200));
                        } else if (item.type == ThrowableItem.TYPE_MOLOTOV) {
                            fireAreas.add(new FireArea(item.targetX, item.targetY, 140, 6000));
                        }
                        itemIt.remove();
                    }
                }

                // 5. الانفجارات
                Iterator<Explosion> expIt = explosions.iterator();
                while (expIt.hasNext()) {
                    Explosion exp = expIt.next();
                    exp.update();
                    if (exp.isFinished()) {
                        expIt.remove();
                    } else if (exp.isDamageActive()) {
                        for (Zombie z : zombies) {
                            float dx = z.x - exp.x;
                            float dy = z.y - exp.y;
                            if (Math.sqrt(dx * dx + dy * dy) <= exp.currentRadius + z.radius) {
                                z.health -= exp.damage;
                            }
                        }
                    }
                }

                // 6. النار
                Iterator<FireArea> fireIt = fireAreas.iterator();
                while (fireIt.hasNext()) {
                    FireArea fire = fireIt.next();
                    fire.update();
                    if (fire.isExpired()) {
                        fireIt.remove();
                    } else {
                        for (Zombie z : zombies) {
                            float dx = z.x - fire.x;
                            float dy = z.y - fire.y;
                            if (Math.sqrt(dx * dx + dy * dy) <= fire.radius) {
                                z.health -= 2;
                            }
                        }
                    }
                }

                // 7. توليد الزومبي
                if (currentTime - lastZombieSpawnTime > 1300) {
                    spawnZombie();
                    lastZombieSpawnTime = currentTime;
                }

                // 8. تحديث الزومبي
                Iterator<Zombie> zombieIt = zombies.iterator();
                while (zombieIt.hasNext()) {
                    Zombie z = zombieIt.next();
                    z.update(player.x, player.y);

                    float dx = player.x - z.x;
                    float dy = player.y - z.y;
                    if (Math.sqrt(dx * dx + dy * dy) < player.radius + z.radius) {
                        player.health -= 10;
                        zombieIt.remove();
                        if (player.health <= 0) isGameOver = true;
                        continue;
                    }

                    Iterator<Bullet> bIt = bullets.iterator();
                    while (bIt.hasNext()) {
                        Bullet b = bIt.next();
                        float bdx = z.x - b.x;
                        float bdy = z.y - b.y;
                        if (Math.sqrt(bdx * bdx + bdy * bdy) < z.radius + b.radius) {
                            z.health -= 45;
                            bIt.remove();
                            break;
                        }
                    }

                    if (z.health <= 0) {
                        zombieIt.remove();
                        score += 10;
                        coins += 5;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void spawnZombie() {
            float x, y;
            if (random.nextBoolean()) {
                x = random.nextBoolean() ? 0 : screenWidth;
                y = random.nextFloat() * screenHeight;
            } else {
                x = random.nextFloat() * screenWidth;
                y = random.nextBoolean() ? 0 : screenHeight;
            }
            zombies.add(new Zombie(x, y));
        }

        private void draw() {
            if (surfaceHolder.getSurface().isValid()) {
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    if (canvas == null) return;

                    canvas.drawColor(Color.parseColor("#12161A"));

                    if (!isGameOver) {
                        for (FireArea fire : fireAreas) {
                            paint.setColor(Color.argb(140, 255, 68, 0));
                            canvas.drawCircle(fire.x, fire.y, fire.radius, paint);
                            paint.setColor(Color.argb(200, 255, 165, 0));
                            canvas.drawCircle(fire.x, fire.y, fire.radius * 0.6f, paint);
                        }

                        paint.setColor(Color.YELLOW);
                        for (Bullet b : bullets) {
                            canvas.drawCircle(b.x, b.y, b.radius, paint);
                        }

                        for (ThrowableItem item : throwables) {
                            paint.setColor(item.type == ThrowableItem.TYPE_GRENADE ? Color.BLACK : Color.RED);
                            canvas.drawCircle(item.currentX, item.currentY, 12, paint);
                        }

                        for (Explosion exp : explosions) {
                            paint.setColor(Color.argb(180, 255, 100, 0));
                            canvas.drawCircle(exp.x, exp.y, exp.currentRadius, paint);
                        }

                        for (Zombie z : zombies) {
                            paint.setColor(Color.parseColor("#4CAF50"));
                            canvas.drawCircle(z.x, z.y, z.radius, paint);

                            paint.setColor(Color.RED);
                            canvas.drawRect(z.x - 20, z.y - 35, z.x + 20, z.y - 30, paint);
                            paint.setColor(Color.GREEN);
                            canvas.drawRect(z.x - 20, z.y - 35, z.x - 20 + (40 * Math.max(0, z.health) / 100f), z.y - 30, paint);
                        }

                        if (player != null) {
                            paint.setColor(Color.CYAN);
                            canvas.drawCircle(player.x, player.y, player.radius, paint);

                            paint.setColor(Color.WHITE);
                            paint.setStrokeWidth(8);
                            float gunX = player.x + (float) Math.cos(player.angle) * (player.radius + 15);
                            float gunY = player.y + (float) Math.sin(player.angle) * (player.radius + 15);
                            canvas.drawLine(player.x, player.y, gunX, gunY, paint);
                            paint.setStrokeWidth(0);
                        }

                        paint.setColor(Color.argb(80, 255, 255, 255));
                        canvas.drawCircle(joystickBaseX, joystickBaseY, joystickRadius, paint);
                        paint.setColor(Color.argb(180, 255, 255, 255));
                        canvas.drawCircle(joystickTouchX, joystickTouchY, 45, paint);

                        paint.setColor(isShootingPressed ? Color.RED : Color.argb(160, 220, 50, 50));
                        canvas.drawRoundRect(shootButton, 25, 25, paint);
                        drawButtonText(canvas, "FIRE", shootButton, 35);

                        paint.setColor(Color.argb(160, 50, 50, 50));
                        canvas.drawRoundRect(grenadeButton, 20, 20, paint);
                        drawButtonText(canvas, "💣 " + (player != null ? player.grenades : 0), grenadeButton, 28);

                        paint.setColor(Color.argb(160, 200, 100, 0));
                        canvas.drawRoundRect(molotovButton, 20, 20, paint);
                        drawButtonText(canvas, "🔥 " + (player != null ? player.molotovs : 0), molotovButton, 28);

                        paint.setColor(Color.argb(200, 255, 193, 7));
                        canvas.drawRoundRect(shopButton, 15, 15, paint);
                        paint.setColor(Color.BLACK);
                        paint.setTextSize(30);
                        paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("🛒 SHOP", shopButton.centerX(), shopButton.centerY() + 10, paint);

                        paint.setTextAlign(Paint.Align.LEFT);
                        paint.setTextSize(38);
                        paint.setColor(Color.WHITE);
                        canvas.drawText("Score: " + score, 40, 50, paint);
                        paint.setColor(Color.YELLOW);
                        canvas.drawText("Coins: $" + coins, 40, 95, paint);
                        paint.setColor(Color.GREEN);
                        canvas.drawText("HP: " + (player != null ? player.health : 0), 40, 140, paint);

                        if (isShopOpen) {
                            drawShopWindow(canvas);
                        }

                    } else {
                        paint.setColor(Color.RED);
                        paint.setTextSize(80);
                        paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f - 50, paint);

                        paint.setColor(Color.WHITE);
                        paint.setTextSize(45);
                        canvas.drawText("Final Score: " + score, screenWidth / 2f, screenHeight / 2f + 20, paint);

                        paint.setColor(Color.YELLOW);
                        canvas.drawText("Tap Anywhere to Restart", screenWidth / 2f, screenHeight / 2f + 100, paint);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }

        private void drawButtonText(Canvas canvas, String text, RectF btn, float size) {
            paint.setColor(Color.WHITE);
            paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, btn.centerX(), btn.centerY() + size / 3, paint);
        }

        private void drawShopWindow(Canvas canvas) {
            paint.setColor(Color.argb(220, 0, 0, 0));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(55);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("WEAPON SHOP", screenWidth / 2f, screenHeight / 2f - 160, paint);

            paint.setColor(Color.DKGRAY);
            canvas.drawRoundRect(buyHealthBtn, 20, 20, paint);
            drawButtonText(canvas, "+50 HP ($30)", buyHealthBtn, 32);

            canvas.drawRoundRect(buyGrenadeBtn, 20, 20, paint);
            drawButtonText(canvas, "+1 Grenade ($50)", buyGrenadeBtn, 32);

            canvas.drawRoundRect(buyMolotovBtn, 20, 20, paint);
            drawButtonText(canvas, "+1 Molotov ($40)", buyMolotovBtn, 32);

            paint.setColor(Color.RED);
            canvas.drawRoundRect(closeShopBtn, 15, 15, paint);
            drawButtonText(canvas, "CLOSE", closeShopBtn, 30);
        }

        private void controlFPS() {
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isInitialized) return true;

            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);

            if (isGameOver) {
                if (action == MotionEvent.ACTION_DOWN) resetGame();
                return true;
            }

            float x = event.getX(pointerIndex);
            float y = event.getY(pointerIndex);

            if (isShopOpen) {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (buyHealthBtn.contains(x, y) && coins >= 30) {
                        coins -= 30;
                        player.health += 50;
                    } else if (buyGrenadeBtn.contains(x, y) && coins >= 50) {
                        coins -= 50;
                        player.grenades++;
                    } else if (buyMolotovBtn.contains(x, y) && coins >= 40) {
                        coins -= 40;
                        player.molotovs++;
                    } else if (closeShopBtn.contains(x, y)) {
                        isShopOpen = false;
                    }
                }
                return true;
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (shopButton.contains(x, y)) {
                        isShopOpen = true;
                    } else if (shootButton.contains(x, y)) {
                        isShootingPressed = true;
                    } else if (grenadeButton.contains(x, y) && player.grenades > 0) {
                        player.grenades--;
                        throwables.add(new ThrowableItem(player.x, player.y, player.angle, ThrowableItem.TYPE_GRENADE));
                    } else if (molotovButton.contains(x, y) && player.molotovs > 0) {
                        player.molotovs--;
                        throwables.add(new ThrowableItem(player.x, player.y, player.angle, ThrowableItem.TYPE_MOLOTOV));
                    } else if (!isJoystickActive && x < screenWidth / 2f) {
                        isJoystickActive = true;
                        joystickPointerId = pointerId;
                        updateJoystick(x, y);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (event.getPointerId(i) == joystickPointerId) {
                            updateJoystick(event.getX(i), event.getY(i));
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (pointerId == joystickPointerId) {
                        isJoystickActive = false;
                        joystickTouchX = joystickBaseX;
                        joystickTouchY = joystickBaseY;
                        joystickPointerId = -1;
                    }
                    if (shootButton.contains(x, y) || event.getPointerCount() == 1) {
                        isShootingPressed = false;
                    }
                    break;
            }
            return true;
        }

        private void updateJoystick(float touchX, float touchY) {
            float dx = touchX - joystickBaseX;
            float dy = touchY - joystickBaseY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < joystickRadius) {
                joystickTouchX = touchX;
                joystickTouchY = touchY;
            } else {
                joystickTouchX = joystickBaseX + (dx / dist) * joystickRadius;
                joystickTouchY = joystickBaseY + (dy / dist) * joystickRadius;
            }
        }

        public void resume() {
            isPlaying = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        public void pause() {
            try {
                isPlaying = false;
                if (gameThread != null) {
                    gameThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class Player {
        float x, y;
        float radius = 35;
        float speed = 7.5f;
        float angle = 0;
        int health = 100;
        int grenades = 2;
        int molotovs = 2;

        Player(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    class Bullet {
        float x, y, speed = 24, radius = 7, dirX, dirY;

        Bullet(float startX, float startY, float angle) {
            this.x = startX;
            this.y = startY;
            this.dirX = (float) Math.cos(angle);
            this.dirY = (float) Math.sin(angle);
        }

        void update() {
            x += dirX * speed;
            y += dirY * speed;
        }
    }

    class ThrowableItem {
        static final int TYPE_GRENADE = 1;
        static final int TYPE_MOLOTOV = 2;

        float currentX, currentY, targetX, targetY;
        float speed = 15;
        int type;

        ThrowableItem(float startX, float startY, float angle, int type) {
            this.currentX = startX;
            this.currentY = startY;
            this.type = type;

            float throwDistance = 350;
            this.targetX = startX + (float) Math.cos(angle) * throwDistance;
            this.targetY = startY + (float) Math.sin(angle) * throwDistance;
        }

        void update() {
            float dx = targetX - currentX;
            float dy = targetY - currentY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist > speed) {
                currentX += (dx / dist) * speed;
                currentY += (dy / dist) * speed;
            } else {
                currentX = targetX;
                currentY = targetY;
            }
        }

        boolean isExploded() {
            return currentX == targetX && currentY == targetY;
        }
    }

    class Explosion {
        float x, y, maxRadius, currentRadius = 10;
        int damage;
        boolean damageApplied = false;

        Explosion(float x, float y, float maxRadius, int damage) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
            this.damage = damage;
        }

        void update() {
            currentRadius += 12;
        }

        boolean isDamageActive() {
            if (!damageApplied) {
                damageApplied = true;
                return true;
            }
            return false;
        }

        boolean isFinished() {
            return currentRadius >= maxRadius;
        }
    }

    class FireArea {
        float x, y, radius;
        long expireTime;

        FireArea(float x, float y, float radius, long duration) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.expireTime = System.currentTimeMillis() + duration;
        }

        void update() {}

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    class Zombie {
        float x, y, speed = 3.2f, radius = 30;
        int health = 100;

        Zombie(float x, float y) {
            this.x = x;
            this.y = y;
        }

        void update(float playerX, float playerY) {
            float dx = playerX - x;
            float dy = playerY - y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist > 0) {
                x += (dx / dist) * speed;
                y += (dy / dist) * speed;
            }
        }
    }
}
