package com.thestbar.ludumdare54.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.thestbar.ludumdare54.*;
import com.thestbar.ludumdare54.gameobjects.*;
import com.thestbar.ludumdare54.listeners.ListenerClass;
import com.thestbar.ludumdare54.managers.SoundManager;
import com.thestbar.ludumdare54.utils.Box2DUtils;
import com.thestbar.ludumdare54.utils.Constants;
import com.thestbar.ludumdare54.utils.LabelStyleUtil;
import com.thestbar.ludumdare54.utils.TiledObjectUtil;

public class GameScreen implements Screen {
    private GameApp game;
    private Box2DDebugRenderer debugRenderer;
    private World world;
    private OrthographicCamera camera;
    private OrthogonalTiledMapRenderer tiledMapRenderer;
    private TiledMap map;
    public static Player player;
    private ListenerClass listener;
    public static Array<Body> bodiesToBeDeleted;
    private MapObject levelEndPos;
    private float playerDiedDeltaTime;
    private SoundManager soundManager;

    // Play again UI
    private Table rootTable;
    private TextButton startGameButton;
    private Label titleLabel;
    private Label nextLabel;

    // Title animation variables
    private float titleLabelSize = 2f;
    private Stage uiStage;
    private ProgressBar uiPlayerHealthBar;

    // Tab menu
    private boolean isPowerupMenuOpen = false;
    private Stage powerupStage;
    private TextureAtlas powerupGuiAtlas;
    private TextureRegion[] powerupTextures;

    // TODO - There is a bug on double jump, when the player goes away from a platform without jumping
    public GameScreen(GameApp game) {
        this.game = game;

        // Load all assets to asset manager
        // Textures (Everything is inside the atlas
        game.assetManager.load("spritesheets/atlas/ld54.atlas", TextureAtlas.class);
        game.assetManager.load("spritesheets/ld54-background.png", Texture.class);
        game.assetManager.load("spritesheets/ld54-black-transparent.png", Texture.class);

        // Music and sound effects
        soundManager = new SoundManager(game.assetManager);

        game.assetManager.finishLoading();
    }

    @Override
    public void show() {
        this.game.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(this.game.stage);

        bodiesToBeDeleted = new Array<>();

        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, width, height);

        world = new World(Constants.GRAVITATIONAL_CONSTANT, false);
        debugRenderer = new Box2DDebugRenderer();

        // The fileName path is based on project's working directory.
        // It is very important to keep the structure of the LDtk program
        // output. The tmx file uses the other resources to construct itself.
        // Remember also to set the "image source" attribute of the tmx file
        // to "image source="../../../spritesheets/ld54-spritesheet.png".
        // This is because we want the tmx file to point to the sprite sheet.
        map = new TmxMapLoader().load("maps/level0/Level_0_v9.tmx");
        tiledMapRenderer = new OrthogonalTiledMapRenderer(map);

        TiledObjectUtil.parseTiledObjectLayer(world, map.getLayers().get("colliders").getObjects());

        // Create player
        MapObjects entities = map.getLayers().get("Entities").getObjects();
        MapObject playerStartPos;

        if (entities.get(0).getName().equals("PlayerStart")) {
            playerStartPos = entities.get(0);
            levelEndPos = entities.get(1);
        } else {
            playerStartPos = entities.get(1);
            levelEndPos = entities.get(0);
        }
        Rectangle rectangle = ((RectangleMapObject) playerStartPos).getRectangle();
        int x = (int) rectangle.x;
        int y = (int) rectangle.y;
        player = new Player(game, world, x, y, soundManager);
        camera.position.x = 143;
        camera.position.y = 807;
        camera.position.z = 10f;
        camera.update();

        rectangle = ((RectangleMapObject) levelEndPos).getRectangle();
        x = (int) rectangle.x;
        y = (int) rectangle.y;
        Body levelEndPoint = Box2DUtils
                .createBox(world, x, y, 16, 10, BodyDef.BodyType.StaticBody);
        levelEndPoint.getFixtureList().get(0).setDensity(1);
        levelEndPoint.getFixtureList().get(0).setFriction(0);
        levelEndPoint.getFixtureList().get(0).getFilterData().categoryBits = Constants.BIT_LEVEL_END;
        levelEndPoint.getFixtureList().get(0).getFilterData().maskBits = Constants.BIT_PLAYER;
        levelEndPoint.getFixtureList().get(0).setSensor(true);
        levelEndPoint.getFixtureList().get(0).setUserData("level_end");

        // Create enemies
        Enemy.createEnemies(game, world, map.getLayers().get("Enemies").getObjects(), soundManager);

        // Create powerups
        Powerup.createPowerups(game, world, map.getLayers().get("Powerups").getObjects());

        // Create lava
        Lava.createLavas(game, world, map.getLayers().get("Lava").getObjects());

        // Play again UI initialization - Is being used and as win screen
        rootTable = new Table();
        rootTable.setFillParent(true);
        game.stage.addActor(rootTable);

        titleLabel = new Label("You Died!", this.game.skin);
        titleLabel.setStyle(LabelStyleUtil.getLabelStyle(this.game, "title", Color.ORANGE));
        titleLabel.setFontScale(titleLabelSize);
        rootTable.add(titleLabel).row();
        titleLabel.setFontScale(0.8f);

        nextLabel = new Label("\"Don't smash the keyboard, yet!\"", this.game.skin);
        nextLabel.setStyle(LabelStyleUtil.getLabelStyle(this.game, "subtitle", Color.WHITE));
        rootTable.add(nextLabel).padTop(50).row();
        nextLabel.setFontScale(0.8f);

        startGameButton = new TextButton("Restart", this.game.skin);
        rootTable.add(startGameButton).padTop(80).row();

        // Build Game UI
        uiStage = new Stage(new ScreenViewport());
        Table uiRootTable = new Table();
        uiRootTable.setSize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiRootTable.top().left();
        uiRootTable.setFillParent(true);
        uiStage.addActor(uiRootTable);

        uiPlayerHealthBar = new ProgressBar(0, player.maxHealthPoints, 1, false, game.skin);
        uiPlayerHealthBar.setStyle(new ProgressBar
                .ProgressBarStyle(game.skin.get("health", ProgressBar.ProgressBarStyle.class)));
        uiPlayerHealthBar.setValue(player.maxHealthPoints);
        uiRootTable.add(uiPlayerHealthBar).width(500).padTop(20).padLeft(20);

        // Powerup menu UI
        powerupGuiAtlas = new TextureAtlas("spritesheets/atlas/ld54-powerups-gui.atlas");
        powerupTextures = new TextureRegion[8];
        powerupTextures[0] = powerupGuiAtlas.findRegion("ld54-powerup-menu");
        powerupTextures[1] = powerupGuiAtlas.findRegion("ld54-powerup-menu-no-grid");
        for (int i = 1; i < 4; ++i) {
            powerupTextures[i + 1] = powerupGuiAtlas.findRegion("ld54-powerup-" + i);
            powerupTextures[i + 4] = powerupGuiAtlas.findRegion("ld54-powerup-inv" + i);
        }

        powerupStage = new Stage(new ScreenViewport());
        Table powerupRootTable = new Table();
        powerupRootTable.setSize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        powerupRootTable.setFillParent(true);
//        powerupRootTable.debug();
        powerupRootTable.top();
        powerupStage.addActor(powerupRootTable);

        Label title = new Label("Power Ups Menu", this.game.skin);
        title.setStyle(LabelStyleUtil.getLabelStyle(this.game, "subtitle", Color.WHITE));
        title.setFontScale(0.8f);
        powerupRootTable.add(title).colspan(2).height(100).padTop(100).row();

        Label title1 = new Label("Combine", this.game.skin);
        title1.setStyle(LabelStyleUtil.getLabelStyle(this.game, "subtitle", Color.WHITE));
        title1.setFontScale(0.6f);
        powerupRootTable.add(title1).colspan(1).padRight(150);

        Label title2 = new Label("Inventory", this.game.skin);
        title2.setStyle(LabelStyleUtil.getLabelStyle(this.game, "subtitle", Color.WHITE));
        title2.setFontScale(0.6f);
        powerupRootTable.add(title2).colspan(1).row();

        listener = new ListenerClass();
        world.setContactListener(listener);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);
        if (!game.assetManager.update(17)) {
            float progress = game.assetManager.getProgress();
            System.out.println("Loading: " + progress);
            return;
        }
        if (!soundManager.isBackgroundMusicOn()) {
            soundManager.playBackgroundMusic();
        }

        game.batch.begin();
        game.batch.draw(game.assetManager.get("spritesheets/ld54-background.png", Texture.class),
                0, 0, camera.viewportWidth * Constants.SCALE,
                camera.viewportHeight * Constants.SCALE * 2);
        game.batch.end();
        inputUpdate(delta);
        cameraUpdate(delta);
        if (!isPowerupMenuOpen) {
            world.step(1/60f, 6, 2);
        } else {
            world.clearForces();
        }
        game.batch.setProjectionMatrix(camera.combined);
        if (!(player.playerState == Player.PlayerState.DIE)) {
            player.render(game.batch, isPowerupMenuOpen);
        } else {
            // This is done to display death animation
            playerDiedDeltaTime += delta;
            if (playerDiedDeltaTime < 1.2f) {
                player.render(game.batch, false);
            }
        }
        for (Enemy enemy : Enemy.enemiesArray) {
            float distanceFromPlayer = player.body.getPosition().dst(enemy.body.getPosition());
            Vector2 dir = player.body.getPosition().cpy().sub(enemy.body.getPosition()).nor();
            boolean flip = dir.x > 0;
            if (distanceFromPlayer <= enemy.range) {
                enemy.attack();
            }
            enemy.render(game.batch, flip, isPowerupMenuOpen);
        }
        for (Powerup powerup : Powerup.powerupsArray) {
            powerup.render(game.batch, isPowerupMenuOpen);
        }
        for (Lava lava : Lava.lavaArray) {
            lava.render(game.batch, isPowerupMenuOpen);
        }
        for (Fireball fireball : Fireball.activeFireballs) {
            fireball.render(game.batch, isPowerupMenuOpen);
        }
        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();
//        debugRenderer.render(world, camera.combined.scl(Constants.PPM));

        // Dispose unused bodies
        for (Body body : bodiesToBeDeleted) {
            world.destroyBody(body);
        }
        bodiesToBeDeleted.clear();

        // Update player health bar
        uiPlayerHealthBar.setValue(player.healthPoints);
        // Render UI

        uiStage.getViewport().apply();
        uiStage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        uiStage.draw();
        game.stage.getViewport().apply();

        // In case player dies render try again screen
        // In case player wins render win screen
        if (player.playerState == Player.PlayerState.DIE ||
            player.playerState == Player.PlayerState.WIN) {
            if (player.playerState == Player.PlayerState.WIN) {
                titleLabel.setText("You Won!");
                nextLabel.setText("Thanks for playing!");
            }
            game.batch.begin();
            game.batch.draw(game.assetManager.get("spritesheets/ld54-black-transparent.png", Texture.class),
                    0, 0, camera.viewportWidth * Constants.PPM, camera.viewportHeight * Constants.PPM);
            game.batch.end();
            game.stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
            game.stage.draw();
            if (startGameButton.isPressed()) {
                // Remember to dispose all the static memory
                soundManager.playSound("button");
                disposeStaticMemory();
                game.setScreen(new GameScreen(game));
            }
        }

        if (isPowerupMenuOpen) {
            // Dim the screen
            game.batch.begin();
            game.batch.draw(game.assetManager.get("spritesheets/ld54-black-transparent.png", Texture.class),
                    0, 0, camera.viewportWidth * Constants.PPM, camera.viewportHeight * Constants.PPM);
            game.batch.draw(powerupTextures[0], camera.position.x - 56, camera.position.y - 33,
                    powerupTextures[0].getRegionWidth() / 3f, powerupTextures[0].getRegionHeight() / 3f);
            game.batch.draw(powerupTextures[1], camera.position.x, camera.position.y - 33,
                    powerupTextures[0].getRegionWidth() / 3f, powerupTextures[0].getRegionHeight() / 3f);
            game.batch.end();
            // Display the power up panel
            System.out.println(player.collectedPowerupTypes);

            int i = 0, j = 0;
            final float size = 53f / 3f;
            for (int powerupTypeId : player.collectedPowerupTypes) {
                float x = camera.position.x + size * i;
                float y = camera.position.y + 2 - size * j;
                game.batch.begin();
                game.batch.draw(powerupTextures[5 + powerupTypeId], x, y, size, size);
                game.batch.end();
                if (++i == 3) {
                    i = 0;
                    ++j;
                }
            }

            powerupStage.getViewport().apply();
            powerupStage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
            powerupStage.draw();



            game.stage.getViewport().apply();
        }
    }

    private void inputUpdate(float delta) {
        if (player.playerState == Player.PlayerState.DIE ||
            player.playerState == Player.PlayerState.WIN) {
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            isPowerupMenuOpen = !isPowerupMenuOpen;
        }
        // In case the powerup menu is open do not let the
        // user move the character
        if (isPowerupMenuOpen) {
            return;
        }
        int horizontalForce = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            horizontalForce -= 1;
            player.playerState = Player.PlayerState.MOVE_LEFT;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            horizontalForce += 1;
            player.playerState = Player.PlayerState.MOVE_RIGHT;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.W) && listener.isPlayerOnGround()) {
            player.jump(800);
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.W) && !listener.isPlayerOnGround() && listener.isAvailableDoubleJump()) {
            player.jump(1000);
            listener.useDoubleJump();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            player.attack();
            // Check for hits on enemies
            int enemyIdBeingHit = listener.getEnemyIdBeingHit();
            if (enemyIdBeingHit != -1 && player.isAttacking()) {
                String enemyId = "enemy" + enemyIdBeingHit;
                Enemy enemy = Enemy.enemiesMap.get(enemyId);
                enemy.hit(player.playerDamage);
            }
        }
        player.move(horizontalForce);
    }

    private void cameraUpdate(float delta) {
//        System.out.println(camera.position);
        Vector3 position = camera.position;
        // b = a + (b - a) * lerp
        // b = target
        // a = current position
        final float lerp = 0.1f;
        position.x = position.x + (player.body.getPosition().x * Constants.PPM - position.x) * lerp;
        position.y = position.y + (player.body.getPosition().y * Constants.PPM - position.y) * lerp;
        position.z = 10f;
        camera.position.set(position);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width / Constants.SCALE, height / Constants.SCALE);
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        world.dispose();
        debugRenderer.dispose();
        player.dispose();
        tiledMapRenderer.dispose();
        map.dispose();
        game.assetManager.clear();
        powerupStage.dispose();
    }

    public void disposeStaticMemory() {
        Enemy.enemiesArray.clear();
        Enemy.enemiesMap.clear();
        Enemy.enemies = 0;
        Fireball.activeFireballs.clear();
        Fireball.fireballMap.clear();
        Fireball.fireballCounter = 0;
        Lava.lavaArray.clear();
        Powerup.powerupMap.clear();
        Powerup.powerupsArray.clear();
        Powerup.powerups = 0;
        GameScreen.bodiesToBeDeleted.clear();
        GameScreen.player = null;
    }
}
