/**
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.configurate.hocon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.AtomicFiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * Basic sanity checks for the loader
 */
public class HoconConfigurationLoaderTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();
    @Test
    public void testSimpleLoading() throws IOException {
        URL url = getClass().getResource("/example.conf");
        final File saveTest = folder.newFile();

        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setSource(Resources.asCharSource(url, UTF_8))
                .setSink(AtomicFiles.asCharSink(saveTest, UTF_8)).build();
        CommentedConfigurationNode node = loader.load();
        assertEquals("unicorn", node.getNode("test", "op-level").getValue());
        assertEquals("dragon", node.getNode("other", "op-level").getValue());
        CommentedConfigurationNode testNode = node.getNode("test");
        assertEquals(" Test node", testNode.getComment().orElse(null));
        assertEquals("dog park", node.getNode("other", "location").getValue());
        loader.save(node);
        assertEquals(Resources.toString(getClass().getResource("/roundtrip-test.conf"), UTF_8), Files
                .toString(saveTest, UTF_8));
    }

    @Test
    public void testSplitLineCommentInput() throws IOException {
        final File saveTo = folder.newFile();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setFile(saveTo)
                .setURL(getClass().getResource("/splitline-comment-input.conf"))
                .build();
        CommentedConfigurationNode node = loader.load();
        System.out.println(node.getOptions().getHeader());
        loader.save(node);

        assertEquals(Resources.toString(getClass().getResource("/splitline-comment-output.conf"), UTF_8), Files.toString(saveTo, UTF_8));
    }

    @Test
    public void testHeaderSaved() throws IOException {
        final File saveTo = folder.newFile();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setFile(saveTo)
                .build();
        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults().setHeader("Hi! I am a header!\n" +
                        "Look at meeeeeee!!!"));
        node.getNode("node").setComment("I have a comment").getNode("party").setValue("now");

        loader.save(node);
        assertEquals(Resources.toString(getClass().getResource("/header.conf"), UTF_8), Files.toString(saveTo, UTF_8));

    }

    @Test
    public void testBooleansNotShared() throws IOException {
        URL url = getClass().getResource("/comments-test.conf");
        final File saveTo = folder.newFile();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setFile(saveTo).setURL(url).build();

        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults());
        node.getNode("test", "third").setValue(false).setComment("really?");
        node.getNode("test", "apple").setComment("fruit").setValue(false);
        node.getNode("test", "donut").setValue(true).setComment("tasty");
        node.getNode("test", "guacamole").setValue(true).setComment("and chips?");

        loader.save(node);
        assertEquals(Resources.toString(url, UTF_8), Files.toString(saveTo, UTF_8));
    }

    @Test
    public void testNewConfigObject() {
        HoconConfigurationLoader subject = HoconConfigurationLoader.builder().build();

        Map<String, ConfigValue> entries = ImmutableMap.of("a", ConfigValueFactory.fromAnyRef("hi"), "b", ConfigValueFactory.fromAnyRef("bye"));
        subject.newConfigObject(entries);
    }

    @Test
    public void testNewConfigList() {
        HoconConfigurationLoader subject = HoconConfigurationLoader.builder().build();

        List<ConfigValue> entries = ImmutableList.of(ConfigValueFactory.fromAnyRef("hello"), ConfigValueFactory.fromAnyRef("goodbye"));
        subject.newConfigList(entries);
    }
}
