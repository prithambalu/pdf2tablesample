/*
    Copyright 2005, 2005 Burcu Yildiz
    Contact: burcu.yildiz@gmail.com

    This file is part of pdf2table.    pdf2table is free software: you can redistribute it and/or modify    it under the terms of the GNU General Public License as published by    the Free Software Foundation, either version 3 of the License, or    (at your option) any later version.    pdf2table is distributed in the hope that it will be useful,    but WITHOUT ANY WARRANTY; without even the implied warranty of    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the    GNU General Public License for more details.    You should have received a copy of the GNU General Public License    along with pdf2table.  If not, see <http://www.gnu.org/licenses/>.
*/

package pdf2xml;

import java.util.ArrayList;
import java.util.List;

public class Column {

    List<Text_Element> cells;
    int left;
    int right;
    int empty_cells;
    int header;

    public Column(int l, int r) {
        this.cells = new ArrayList<Text_Element>();
        this.left = l;
        this.right = r;
        this.empty_cells = 0;
        this.header = -1;
    }

    public Column() {
        this(-1, -1);
    }

    public Object clone() {
        Column c = new Column(this.left, this.right);
        c.cells.addAll(this.cells);
        return c;
    }

    public void add(Text_Element t) {
        if (left == -1) {
            left = t.left;
        } else {
            Math.min(left, t.left);
        }
        right = Math.max(right, t.left + t.width);
    }

    public void add(Column c2) {
        left = Math.min(left, c2.left);
        right = Math.max(right, c2.right);
    }
}