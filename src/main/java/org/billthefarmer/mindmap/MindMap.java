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
import android.os.Build;
import android.os.Bundle;

import com.mindsync.library.MindMapView;
import com.mindsync.library.data.CirclePath;
import com.mindsync.library.data.NodeData;
import com.mindsync.library.data.NodePath;
import com.mindsync.library.data.RectanglePath;
import com.mindsync.library.data.Tree;

import java.util.List;

public class MindMap extends Activity
{
    public static final String TAG = "MindMap";

    private MindMapView mindMapView;
    private Tree<Node> tree;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // inflate and create the view
        setContentView(R.layout.main);

        tree = new Tree<>(this);
        mindMapView.setTree(tree);
        mindMapView.initialize();
        mindMapView.setNodeClickListener((NodeData<?> node) ->
        {
            Node selectedNode = createNode(node);
            // ...
        });
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
