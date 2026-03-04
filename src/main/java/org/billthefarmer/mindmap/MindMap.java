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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.mindsync.library.MindMapView;
import com.mindsync.library.data.CirclePath;
import com.mindsync.library.data.CircleNodeData;
import com.mindsync.library.data.NodeData;
import com.mindsync.library.data.NodePath;
import com.mindsync.library.data.RectanglePath;
import com.mindsync.library.data.RectangleNodeData;
import com.mindsync.library.data.Tree;
import com.mindsync.library.util.Dp;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;

public class MindMap extends Activity
{
    public static final String TAG = "MindMap";

    public static final String ID = "id";
    public static final String NODES = "nodes";
    public static final String PARENT = "parent";
    public static final String CONTENT = "content";
    public static final String APPLICATION_JSON = "application/json";

    public static final int OPEN_DOCUMENT = 1;
    public static final int CREATE_DOCUMENT = 2;

    private MindMapView mindMapView;
    private Tree<Node> tree;
    private Node selectedNode;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // inflate and create the view
        setContentView(R.layout.main);
        mindMapView = findViewById(R.id.mind_map_view);

        tree = new Tree<>(this);
        mindMapView.setTree(tree);
        mindMapView.initialize();

        mindMapView.setNodeClickListener((NodeData<?> node) ->
        {
            selectedNode = createNode(node);
            invalidateOptionsMenu();
            // ...
        });
    }

    // onRestoreInstanceState
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        List<String> list = savedInstanceState.getStringArrayList(NODES);
        if (list != null)
        {
            for (String id: list)
            {
                if (id == tree.getRootNode().getId())
                    continue;

                Bundle bundle = savedInstanceState.getBundle(id);
                if (bundle == null)
                    continue;

                String parentId = bundle.getString(PARENT);
                String content = bundle.getString(CONTENT);
                tree.addNode(id, parentId, content);
            }
            mindMapView.animateTreeChange();
            mindMapView.requestLayout();
            mindMapView.getMindMapManager().setSelectedNode(tree.getRootNode());
            mindMapView.postDelayed(() ->
            {
                mindMapView.addNode(TAG);
                mindMapView.getMindMapManager()
                    .setSelectedNode(mindMapView.getAddNode());
                mindMapView.postDelayed(() -> mindMapView.removeNode(), 100);
            }, 100);
        }
    }

    // onSaveInstanceState
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        ArrayList<String> list = new ArrayList<>();
        NodeData<?> root = tree.getRootNode();
        for (String id: root.getChildren())
        {
            list.add(id);
            NodeData<?> node = tree.getNode(id);
            Bundle bundle = new Bundle();
            bundle.putString(PARENT, node.getParentId());
            bundle.putString(CONTENT, node.getDescription());
            outState.putBundle(id, bundle);
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
        menu.findItem(R.id.action_add).setVisible(selectedNode != null);
        menu.findItem(R.id.action_remove)
            .setVisible(selectedNode != null && selectedNode.getId() !=
                        tree.getRootNode().getId());
        menu.findItem(R.id.action_edit).setVisible(selectedNode != null);

        return true;
    }

    // On options item selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	// Get id
	int id = item.getItemId();
	switch (id)
	{
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
        list.add(id);
        NodeData<?> node = tree.getNode(id);
        Bundle bundle = new Bundle();
        bundle.putString(PARENT, node.getParentId());
        bundle.putString(CONTENT, node.getDescription());
        outState.putBundle(id, bundle);
        for (String child: node.getChildren())
            saveState(outState, list, child);
    }

    // add
    private void add()
    {
        addDialog(R.string.addNode, R.string.nodeDesc);
    }

    // remove
    private void remove()
    {
        alertDialog(R.string.remNode, R.string.nodeRem, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE: 
                mindMapView.removeNode();
                break;
            }
        });
    }

    // edit
    private void edit()
    {
        descriptionDialog(R.string.editNode, R.string.nodeDesc,
                          selectedNode.getDescription());
    }

    // fit
    private void fit()
    {
        mindMapView.fitScreen();
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

    // createNode
    public Node createNode(NodeData<?> node)
    {
        if (node instanceof CircleNodeData)
        {
            CircleNodeData circleNodeData = (CircleNodeData) node;
            return new CircleNode(
                circleNodeData.getId(),
                circleNodeData.getParentId(),
                new CirclePath(
                    new Dp(circleNodeData.getPath().getCenterX().getDpVal()),
                    new Dp(circleNodeData.getPath().getCenterY().getDpVal()),
                    new Dp(circleNodeData.getPath().getRadius().getDpVal())),
                circleNodeData.getDescription(),
                circleNodeData.getChildren());
            }

        else if (node instanceof RectangleNodeData)
        {
            RectangleNodeData rectangleNodeData = (RectangleNodeData) node;
            return new RectangleNode(
                rectangleNodeData.getId(),
                rectangleNodeData.getParentId(),
                new RectanglePath(
                    new Dp(rectangleNodeData.getPath().getCenterX().getDpVal()),
                    new Dp(rectangleNodeData.getPath().getCenterY().getDpVal()),
                    new Dp(rectangleNodeData.getPath().getWidth().getDpVal()),
                    new Dp(rectangleNodeData.getPath().getHeight().getDpVal())),
                rectangleNodeData.getDescription(),
                rectangleNodeData.getChildren());
        }

        else
            return null;
    }

    // openFile
    private void openFile()
    {

        NodeData<?> root = tree.getRootNode();
        if (!root.getChildren().isEmpty())
            alertDialog(R.string.appName, R.string.replace, (dialog, id) ->
        {
            switch(id)
            {
            case DialogInterface.BUTTON_POSITIVE:
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
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader
             (new InputStreamReader(new BufferedInputStream
               (getContentResolver().openInputStream(uri)))))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
        }

        catch (Exception e)
        {
            alertDialog(R.string.appName, e.getMessage());
            e.printStackTrace();
        }

        try
        {
            JSONObject object = new JSONObject(builder.toString());
            if (!TAG.equals(object.getString(CONTENT)))
                throw new Exception(getResources().getString(R.string.invalid));

            tree = new Tree(this);
            mindMapView.setTree(tree);
            mindMapView.initialize();
            JSONArray array = object.getJSONArray(NODES);
            for (int i = 0; i < array.length(); i++)
            {
                JSONObject node = array.getJSONObject(i);
                String id = node.getString(ID);
                String parentId = node.getString(PARENT);
                String content = node.getString(CONTENT);
                tree.addNode(id, parentId, content);
            }

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
        intent.putExtra(Intent.EXTRA_TITLE, TAG);
        startActivityForResult(intent, CREATE_DOCUMENT);
    }

    // saveFile
    private void saveFile(Uri uri)
    {
        JSONObject object = new JSONObject();
        try
        {
            object.put(CONTENT, TAG);
            JSONArray array = new JSONArray();
            NodeData<?> root = tree.getRootNode();
            for (String id: root.getChildren())
            {
                NodeData<?> node = tree.getNode(id);
                JSONObject entry = new JSONObject();
                entry.put(ID, node.getId());
                entry.put(PARENT, node.getParentId());
                entry.put(CONTENT, node.getDescription());
                array.put(entry);
                for (String child: node.getChildren())
                    saveNodes(array, child);
            }
            object.put(NODES, array);
            Log.d(TAG, "Nodes " + object.toString(4));
        }

        catch (Exception e) {}

        try (OutputStreamWriter writer = new OutputStreamWriter
             (getContentResolver().openOutputStream(uri, "rwt")))
        {
            writer.append(object.toString());
            writer.flush();
        }

        catch (Exception e)
        {
            alertDialog(R.string.appName, e.getMessage());
            e.printStackTrace();
        }
    }

    // saveNodes
    private void saveNodes(JSONArray array, String id)
    {
        NodeData<?> node = tree.getNode(id);
        try
        {
            JSONObject entry = new JSONObject();
            entry.put(ID, node.getId());
            entry.put(PARENT, node.getParentId());
            entry.put(CONTENT, node.getDescription());
            array.put(entry);
        }

        catch (Exception e)
        {
            return;
        }
        for (String child: node.getChildren())
            saveNodes(array, child);
    }

    // Node
    abstract class Node
    {
        protected final String id;
        protected final String parentId;
        protected final NodePath path;
        protected final String description;
        protected final List<String> children;

    public Node(String id, String parentId, NodePath path,
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

        public NodePath getPath()
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

        public abstract Node adjustPosition(Dp horizontalSpacing,
                                            Dp totalHeight);
    }

    // CircleNode
    final class CircleNode extends Node
    {
        public CircleNode(String id, String parentId, CirclePath path,
                          String description, List<String> children)
        {
            super(id, parentId, path, description, children);
        }

        @Override
        public CirclePath getPath() {
            return (CirclePath) path;
        }

        @Override
        public CircleNode adjustPosition(Dp horizontalSpacing, Dp totalHeight)
        {
            CirclePath newPath = getPath().adjustPath(horizontalSpacing,
                                                      totalHeight);
            return new CircleNode(id, parentId, newPath, description, children);
        }
    }

    // RectangleNode
    final class RectangleNode extends Node
    {
        public RectangleNode(String id, String parentId, RectanglePath path,
                             String description, List<String> children)
        {
            super(id, parentId, path, description, children);
        }

        @Override
        public RectanglePath getPath()
        {
            return (RectanglePath) path;
        }

        @Override
        public RectangleNode adjustPosition(Dp horizontalSpacing,
                                            Dp totalHeight)
        {
            RectanglePath newPath = getPath().adjustPath(horizontalSpacing,
                                                         totalHeight);
            return new RectangleNode(id, parentId, newPath, description,
                                     children);
        }
    }
}
