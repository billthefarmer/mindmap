////////////////////////////////////////////////////////////////////////////////
//
//  MindMap - An Android mind map app.
//
//  Copyright (C) 2026	Bill Farmer
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//
///////////////////////////////////////////////////////////////////////////////

package org.billthefarmer.mindmap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.JsonWriter;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toolbar;

import com.mindsync.library.MindMapView;
import com.mindsync.library.data.CirclePath;
import com.mindsync.library.data.CircleNodeData;
import com.mindsync.library.data.NodeData;
import com.mindsync.library.data.NodePath;
import com.mindsync.library.data.RectanglePath;
import com.mindsync.library.data.RectangleNodeData;
import com.mindsync.library.data.Tree;
import com.mindsync.library.util.Dp;

import java.text.DateFormat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.json.JSONArray;

public class MindMap extends Activity
    implements PopupMenu.OnMenuItemClickListener
{
    public static final String TAG = "MindMap";

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String ROOT = "root";
    public static final String NODES = "nodes";
    public static final String PARENT = "parent";
    public static final String CONTENT = "content";

    public static final String APPLICATION_JSON = "application/json";

    public static final int OPEN_DOCUMENT = 1;
    public static final int CREATE_DOCUMENT = 2;
    public static final int NODE_DELAY = 2;

    private MindMapView mindMapView;
    private Toolbar toolbar;
    private Tree<Node> tree;
    private Node selectedNode;

    private String name = TAG;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // inflate and create the view
        setContentView(R.layout.main);

        // Find toolbar
        toolbar = findViewById(getResources().getIdentifier("action_bar",
                                                            "id", "android"));
        // Set up navigation
        toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp);
        toolbar.setNavigationOnClickListener((v) ->
        {
            PopupMenu popup = new PopupMenu(this, v);
            popup.inflate(R.menu.navigation);
            popup.setOnMenuItemClickListener(this);
            popup.show();
        });

        mindMapView = findViewById(R.id.mind_map_view);
        // Create tree
        tree = new Tree<>(this);
        mindMapView.setTree(tree);
        mindMapView.initialize();

        // Check if we have a file
        if (savedInstanceState == null)
        {
            Intent intent = getIntent();

            // Check action
            if (Intent.ACTION_VIEW.equals(intent.getAction()) ||
                Intent.ACTION_EDIT.equals(intent.getAction()))
                openFile(intent.getData());
        }

        // Check for selected node
        mindMapView.setNodeClickListener((NodeData<?> node) ->
        {
            selectedNode = createNode(node);
            invalidateOptionsMenu();
        });
    }

    // onRestoreInstanceState
    @Override
    @SuppressWarnings("deprecation")
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);

        // Get the map name
        name = savedInstanceState.getString(NAME);
        setTitle(name);

        NodeData<?> root = (NodeData)savedInstanceState.getSerializable
            (tree.getRootNode().getId());
        tree.setRootNode(root);
        // Get the nodes
        List<String> list = savedInstanceState.getStringArrayList(NODES);
        if (list != null)
        {
            for (String id: list)
            {
                NodeData<?> node =
                    (NodeData)savedInstanceState.getSerializable(id);
                tree.setNode(id, node);
            }
            // Jiggery pokery to restore the display
            mindMapView.animateTreeChange();
            mindMapView.requestLayout();
            mindMapView.postDelayed(() ->
            {
                mindMapView.addNode(TAG);
                mindMapView.getMindMapManager()
                    .setSelectedNode(mindMapView.getAddNode());
                mindMapView.postDelayed
                    (() -> mindMapView.removeNode(), NODE_DELAY);
            }, NODE_DELAY);
        }
        mindMapView.fitScreen();
        invalidateOptionsMenu();
    }

    // onSaveInstanceState
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        // Save the map name
        outState.putString(NAME, name);
        // Save the root node
        NodeData<?> root = tree.getRootNode();
        if (root.getChildren().isEmpty())
            return;
        Log.d(TAG, "Node " + root.getId());
        outState.putSerializable(root.getId(), root);
        // Save the nodes
        ArrayList<String> list = new ArrayList<>();
        for (String id: root.getChildren())
        {
            Log.d(TAG, "Node " + id);
            list.add(id);
            NodeData<?> node = tree.getNode(id);
            outState.putSerializable(id, node);
            for (String child: node.getChildren())
                saveState(outState, list, child);
        }

        outState.putStringArrayList(NODES, list);
    }

    // On create options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	// Inflate the menu; this adds items to the action bar if it
	// is present.
	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.main, menu);

	return true;
    }

    // onPrepareOptionsMenu
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        // Adjust the menu according to what is selected
        menu.findItem(R.id.action_new)
            .setVisible(!tree.getRootNode().getChildren().isEmpty());
        menu.findItem(R.id.action_add).setVisible(selectedNode != null);
        menu.findItem(R.id.action_remove)
            .setVisible(selectedNode != null && selectedNode.getId() !=
                        tree.getRootNode().getId());
        menu.findItem(R.id.action_edit).setVisible(selectedNode != null);

        return true;
    }

    // onNewIntent
    @Override
    public void onNewIntent(Intent intent)
    {
        // Check if we have a tree
        NodeData<?> root = tree.getRootNode();
        if (!root.getChildren().isEmpty())
            alertDialog(R.string.appName, R.string.replace, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE:
                openFile(intent.getData());
                break;
            }
        });
    }

    // On options item selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	// Get id
	int id = item.getItemId();
	switch (id)
	{
            // New
        case R.id.action_new:
            newTree();
            break;

            // Add
        case R.id.action_add:
            add();
            break;

            // Help
        case R.id.action_remove:
            remove();
            break;

            // Edit
        case R.id.action_edit:
            edit();
            break;

            // Fit
        case R.id.action_fit:
            fit();
            break;

            // Save
        case R.id.action_open:
            openFile();
            break;

            // Save
        case R.id.action_save:
            saveFile();
            break;

        default:
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    // onMenuItemClick
    @Override
    public boolean onMenuItemClick(MenuItem item)
    {
        // Get id
        int id = item.getItemId();
        switch (id)
        {
        case R.id.action_help:
            help();
            break;

        case R.id.action_about:
            about();
            break;

        default:
            return false;
        }

        return true;
    }

    // onBackPressed
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed()
    {
        // Check if we have a tree
        NodeData<?> root = tree.getRootNode();
        if (!root.getChildren().isEmpty())
        {
            alertDialog(R.string.appName, R.string.back, (dialog, id) ->
            {
                switch (id)
                {
                case DialogInterface.BUTTON_POSITIVE:
                    saveFile();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    finish();
                    break;
                }
            });
        }

        else
        {
            setResult(RESULT_CANCELED, null);
            finish();
        }
    }

    // onActivityResult
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data)
    {
        // Do nothing if cancelled
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode)
        {
        case OPEN_DOCUMENT:
            // Check data
            if (data == null || data.getData() == null)
                return;

            openFile(data.getData());
            break;

        case CREATE_DOCUMENT:
            // Check data
            if (data == null || data.getData() == null)
                return;

            saveFile(data.getData());
            break;
        }
    }

    // saveState
    private void  saveState(Bundle outState, List<String> list, String id)
    {
        Log.d(TAG, "Node " + id);
        // Save a node
        list.add(id);
        NodeData<?> node = tree.getNode(id);
        outState.putSerializable(id, node);
        // And child nodes
        for (String child: node.getChildren())
            saveState(outState, list, child);
    }

    // new
    private void newTree()
    {
        // Check if we have a tree
        NodeData<?> root = tree.getRootNode();
        if (!root.getChildren().isEmpty())
            alertDialog(R.string.appName, R.string.replace, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE:
                tree = new Tree<>(this);
                mindMapView.setTree(tree);
                mindMapView.initialize();
                name = TAG;
                setTitle(name);
                recreate();
                break;
            }
        });
    }

    // add
    private void add()
    {
        // Show dialog
        addDialog(R.string.addNode, R.string.nodeDesc);
    }

    // remove
    private void remove()
    {
        // Show dialog
        alertDialog(R.string.remNode, R.string.nodeRem, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE:
                mindMapView.removeNode();
                invalidateOptionsMenu();
                break;
            }
        });
    }

    // edit
    private void edit()
    {
        // Show dialog
        descriptionDialog(R.string.editNode, R.string.nodeDesc,
                          selectedNode.getDescription());
    }

    // fit
    private void fit()
    {
        mindMapView.fitScreen();
    }

    // help
    private void help()
    {
        Intent intent = new Intent(this, Help.class);
        startActivity(intent);
    }

    // about
    @SuppressWarnings("deprecation")
    private void about()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.appName);
        builder.setIcon(R.drawable.ic_launcher);

        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        SpannableStringBuilder spannable =
            new SpannableStringBuilder(getText(R.string.version));
        Pattern pattern = Pattern.compile("%s");
        Matcher matcher = pattern.matcher(spannable);
        if (matcher.find())
            spannable.replace(matcher.start(), matcher.end(),
                              BuildConfig.VERSION_NAME);
        matcher.reset(spannable);
        if (matcher.find())
            spannable.replace(matcher.start(), matcher.end(),
                              dateFormat.format(BuildConfig.BUILT));
        builder.setMessage(spannable);

        // Add the button
        builder.setPositiveButton(android.R.string.ok, null);

        // Create the AlertDialog
        Dialog dialog = builder.show();

        // Set movement method
        TextView text = dialog.findViewById(android.R.id.message);
        if (text != null)
        {
            text.setTextAppearance(builder.getContext(),
                                   android.R.style.TextAppearance_Small);
            text.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    // addDialog
    private void addDialog(int title, int message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        // Add the buttons
        builder.setPositiveButton(android.R.string.ok, (dialog, id) ->
        {
            TextView desc = ((Dialog)dialog).findViewById(R.id.description);
            mindMapView.addNode(desc.getText().toString());
            invalidateOptionsMenu();
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        // Create edit text
        LayoutInflater inflater = (LayoutInflater) builder.getContext()
            .getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.description, null);
        builder.setView(view);

        // Create the AlertDialog
        AlertDialog dialog = builder.show();
        TextView desc = dialog.findViewById(R.id.description);
        desc.setText(R.string.node);
    }

    // descriptionDialog
    private void descriptionDialog(int title, int message, String description)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        // Add the buttons
        builder.setPositiveButton(android.R.string.ok, (dialog, id) ->
        {
            TextView desc = ((Dialog)dialog).findViewById(R.id.description);
            mindMapView.editNodeText(desc.getText().toString());
            invalidateOptionsMenu();
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        // Create edit text
        LayoutInflater inflater = (LayoutInflater) builder.getContext()
            .getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.description, null);
        builder.setView(view);

        // Create the AlertDialog
        AlertDialog dialog = builder.show();
        TextView text = dialog.findViewById(R.id.description);
        text.setText(description);
    }

    // alertDialog
    private void alertDialog(int title, int message,
                             DialogInterface.OnClickListener listener)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        // Add the buttons
        builder.setPositiveButton(android.R.string.ok, listener);
        builder.setNegativeButton(android.R.string.cancel, listener);

        // Create the AlertDialog
        builder.show();
    }

    // alertDialog
    private void alertDialog(int title, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        // Add the buttons
        builder.setPositiveButton(android.R.string.ok, null);

        // Create the AlertDialog
        builder.show();
    }

    // openFile
    private void openFile()
    {
        // Check if we have a tree
        NodeData<?> root = tree.getRootNode();
        if (!root.getChildren().isEmpty())
            alertDialog(R.string.appName, R.string.replace, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE:
                tree = new Tree<>(this);
                mindMapView.setTree(tree);
                mindMapView.initialize();
                name = TAG;
                setTitle(name);
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType(APPLICATION_JSON);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, TAG);
                startActivityForResult(intent, OPEN_DOCUMENT);
                break;
            }
        });

        else
        {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(APPLICATION_JSON);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, TAG);
            startActivityForResult(intent, OPEN_DOCUMENT);
        }
    }

    // openFile
    private void openFile(Uri uri)
    {
        try (JsonReader reader = new JsonReader(new InputStreamReader
              (getContentResolver().openInputStream(uri))))
        {
            reader.beginObject();
            while (reader.hasNext())
            {
                switch (reader.nextName())
                {
                case CONTENT:
                    // Check it's a MindMap file
                    if (!TAG.equals(reader.nextString()))
                        throw new Exception
                            (getResources().getString(R.string.invalid));
                    tree = new Tree<>(this);
                    mindMapView.setTree(tree);
                    mindMapView.initialize();
                    break;

                case ROOT:
                    // Get the root node description
                    NodeData<?> root = tree.getRootNode();
                    mindMapView.getMindMapManager().setSelectedNode(root);
                    mindMapView.editNodeText(reader.nextString());
                    break;

                case NODES:
                    // Get the nodes
                    reader.beginArray();
                    while (reader.hasNext())
                    {
                        String id = null;
                        String parentId = null;
                        String content = null;
                        reader.beginObject();
                        while (reader.hasNext())
                        {
                            switch (reader.nextName())
                            {
                            case ID:
                                id = reader.nextString();
                                break;

                            case PARENT:
                                parentId = reader.nextString();
                                break;

                            case CONTENT:
                                content = reader.nextString();
                                break;
                            }
                        }
                        reader.endObject();
                        tree.addNode(id, parentId, content);
                    }
                    reader.endArray();
                }
            }
            reader.endObject();
            // Get the name
            name = (queryName(uri)).replace(".json", "");
            setTitle(name);
            // Force redisplay
            recreate();
        }

        catch (Exception e)
        {
            alertDialog(R.string.appName, e.getMessage());
            e.printStackTrace();
        }
    }

    // saveFile
    private void saveFile()
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(APPLICATION_JSON);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, CREATE_DOCUMENT);
    }

    // saveFile
    private void saveFile(Uri uri)
    {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter
              (getContentResolver().openOutputStream(uri, "rwt"))))
        {
            writer.beginObject();
            // Save the file marker
            writer.name(CONTENT).value(TAG);
            // Save the root node name
            NodeData<?> root = tree.getRootNode();
            writer.name(root.getId()).value(root.getDescription());
            writer.name(NODES);
            // Save the nodes
            writer.beginArray();
            // Child nodes
            for (String id: root.getChildren())
            {
                NodeData<?> node = tree.getNode(id);
                writer.beginObject();
                writer.name(ID).value(node.getId());
                writer.name(PARENT).value(node.getParentId());
                writer.name(CONTENT).value(node.getDescription());
                writer.endObject();
                // Child nodes
                for (String child: node.getChildren())
                    saveNodes(writer, child);
            }
            writer.endArray();
            writer.endObject();
        }

        catch (Exception e)
        {
            alertDialog(R.string.appName, e.getMessage());
            e.printStackTrace();
        }
    }

    // saveNodes
    private void saveNodes(JsonWriter writer, String id)
        throws Exception
    {
        // Save a node
        NodeData<?> node = tree.getNode(id);
        writer.beginObject();
        writer.name(ID).value(node.getId());
        writer.name(PARENT).value(node.getParentId());
        writer.name(CONTENT).value(node.getDescription());
        writer.endObject();
        // Child nodes
        for (String child: node.getChildren())
            saveNodes(writer, child);
    }

    // queryName
    private String queryName(Uri uri)
    {
        // Get the uri display name
        String[] projection = new String[] {OpenableColumns.DISPLAY_NAME};
        try (Cursor cursor =
             getContentResolver().query(uri, projection, null, null, null))
        {
            if (cursor != null)
            {
                cursor.moveToFirst();
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
        }

        catch (Exception e) {}
        return null;
    }

    // createNode
    public Node createNode(NodeData<?> node)
    {
        if (node instanceof CircleNodeData)
        {
            CircleNodeData circleNodeData = (CircleNodeData) node;
            return new CircleNode(
                circleNodeData.getId(),
                circleNodeData.getParentId(),
                new Circle(
                    circleNodeData.getPath().getCenterX().getDpVal(),
                    circleNodeData.getPath().getCenterY().getDpVal(),
                    circleNodeData.getPath().getRadius().getDpVal()),
                circleNodeData.getDescription(),
                circleNodeData.getChildren());
            }

        else if (node instanceof RectangleNodeData)
        {
            RectangleNodeData rectangleNodeData = (RectangleNodeData) node;
            return new RectangleNode(
                rectangleNodeData.getId(),
                rectangleNodeData.getParentId(),
                new Rectangle(
                    rectangleNodeData.getPath().getCenterX().getDpVal(),
                    rectangleNodeData.getPath().getCenterY().getDpVal(),
                    rectangleNodeData.getPath().getWidth().getDpVal(),
                    rectangleNodeData.getPath().getHeight().getDpVal()),
                rectangleNodeData.getDescription(),
                rectangleNodeData.getChildren());
        }

        else
            return null;
    }

    // createNodeData
    public NodeData createNodeData(Node node)
    {
        if (node instanceof CircleNode)
        {
            CircleNode circleNode = (CircleNode) node;
            return new CircleNodeData(
                circleNode.getId(),
                circleNode.getParentId(),
                new CirclePath(
                    new Dp(circleNode.getPath().getCentreX()),
                    new Dp(circleNode.getPath().getCentreY()),
                    new Dp(circleNode.getPath().getRadius())),
                circleNode.getDescription(),
                circleNode.getChildren(),
                0f, false, false, 1f);
        }

        else if (node instanceof RectangleNode)
        {
            RectangleNode rectangleNode = (RectangleNode) node;
            return new RectangleNodeData(
                rectangleNode.getId(),
                rectangleNode.getParentId(),
                new RectanglePath(
                    new Dp(rectangleNode.getPath().getCentreX()),
                    new Dp(rectangleNode.getPath().getCentreY()),
                    new Dp(rectangleNode.getPath().getWidth()),
                    new Dp(rectangleNode.getPath().getHeight())),
                rectangleNode.getDescription(),
                rectangleNode.getChildren(),
                0f, false, false, 1f);
        }

        else
            return null;
    }

    // Node
    abstract class Node
        implements Serializable
    {
        public final String id;
        public final String parentId;
        public final Path path;
        public final String description;
        public final List<String> children;

        public Node(String id, String parentId, Path path,
                    String description, List<String> children)
        {
            this.id = id;
            this.parentId = parentId;
            this.path = path;
            this.description = description;
            this.children = children;
        }

        public String getId()
        {
            return id;
        }

        public String getParentId()
        {
            return parentId;
        }

        public Path getPath()
        {
            return path;
        }

        public String getDescription()
        {
            return description;
        }

        public List<String> getChildren()
        {
            return children;
        }

        public abstract Node adjustPosition(Float horizontalSpacing,
                                            Float totalHeight);
    }

    // CircleNode
    final class CircleNode extends Node
    {
        public CircleNode(String id, String parentId, Circle path,
                          String description, List<String> children)
        {
            super(id, parentId, path, description, children);
        }

        @Override
        public Circle getPath() {
            return (Circle) path;
        }

        @Override
        public CircleNode adjustPosition(Float horizontalSpacing, Float totalHeight)
        {
            Circle newPath = getPath().adjustPath(horizontalSpacing,
                                                  totalHeight);
            return new CircleNode(id, parentId, newPath, description, children);
        }
    }

    // RectangleNode
    final class RectangleNode extends Node
    {
        public RectangleNode(String id, String parentId, Rectangle path,
                             String description, List<String> children)
        {
            super(id, parentId, path, description, children);
        }

        @Override
        public Rectangle getPath()
        {
            return (Rectangle) path;
        }

        @Override
        public RectangleNode adjustPosition(Float horizontalSpacing,
                                            Float totalHeight)
        {
            Rectangle newPath = getPath().adjustPath(horizontalSpacing,
                                                     totalHeight);
            return new RectangleNode(id, parentId, newPath, description,
                                     children);
        }
    }

    // Path
    abstract class Path
        implements Serializable
    {
        public final Float centreX;
        public final Float centreY;

        public Path(Float centreX, Float centreY)
        {
            this.centreX = centreX;
            this.centreY = centreY;
        }

        public Float getCentreX()
        {
            return centreX;
        }

        public Float getCentreY()
        {
            return centreY;
        }

        public abstract Path adjustPath(Float horizontalSpacing,
                                        Float totalHeight);
    }

    // Circle
    final class Circle
        extends Path
    {
        public final Float radius;

        public Circle(Float centreX, Float centreY, Float radius)
        {
            super(centreX, centreY);
            this.radius = radius;
        }

        public Float getRadius()
        {
            return radius;
        }

        @Override
        public Circle adjustPath(Float horizontalSpacing,
                                 Float totalHeight)
        {
            return new Circle(centreX, centreY + horizontalSpacing,
                              totalHeight / 2);                     
        }
    }

    // Rectangle
    final class Rectangle
        extends Path
    {
        public final Float width;
        public final Float height;

        public Rectangle(Float centreX, Float centreY,
                         Float width, Float height)
        {
            super(centreX, centreY);
            this.width = width;
            this.height = height;
        }

        public Float getWidth()
        {
            return width;
        }

        public Float getHeight()
        {
            return height;
        }

        @Override
        public Rectangle adjustPath(Float horizontalSpacing,
                                    Float totalHeight)
        {
            return new Rectangle(centreX, centreY + horizontalSpacing,
                                 width, totalHeight);                     
        }
    }
}
