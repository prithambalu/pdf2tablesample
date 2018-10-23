/*
    Copyright 2005, 2005 Burcu Yildiz
    Contact: burcu.yildiz@gmail.com
    
    This file is part of pdf2table.    pdf2table is free software: you can redistribute it and/or modify    it under the terms of the GNU General Public License as published by    the Free Software Foundation, either version 3 of the License, or    (at your option) any later version.    pdf2table is distributed in the hope that it will be useful,    but WITHOUT ANY WARRANTY; without even the implied warranty of    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the    GNU General Public License for more details.    You should have received a copy of the GNU General Public License    along with pdf2table.  If not, see <http://www.gnu.org/licenses/>.
*/

package pdf2xml;

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import pdf2xml.Text_Element.Type;


/**
 * Comparator to sort elements by the Y values of the tops.
 */
class TopElementComparator implements Comparator<Element> {

    public int compare(Element e1, Element e2) {
        int top1 = Integer.parseInt(e1.getAttribute("top").getValue());
        int top2 = Integer.parseInt(e2.getAttribute("top").getValue());
        return (top1 - top2);
    }
}


public class FirstClassification {

    List<Font> fonts;
    List<Line> lines;
    List<Multiline_Block> mlbs;
    boolean interactive_extraction;
    String path;
    
    int distance_sum = 0;

    public FirstClassification(boolean interactivity, String p) {//, int c) {
        this.fonts = new ArrayList<Font>();
        this.lines = new ArrayList<Line>();
        this.mlbs = new ArrayList<Multiline_Block>();
        this.interactive_extraction = interactivity;
        this.path = p;
    }

    
    public void run(final String file_name) {

        SAXBuilder builder = new SAXBuilder();
        try {
            int lines_before = 0;
            Document doc = builder.build(file_name);

            for (Element page : doc.getRootElement().getChildren("page")) {
                lines_before = doPage(lines_before, page);
            } // end of while pages

            multiline_block_merge(this.mlbs, this.lines);

            List<Table> tables = SecondClassification.decompose_tables(mlbs, lines);

            if (interactive_extraction == true) {
                SemiOutputFrame so = new SemiOutputFrame(tables, fonts, path);
                so.setVisible(true);
            } else {
                XmlOutput.create(tables, fonts, path);
            }
        } catch (JDOMException e) {
            System.out.println(e.getMessage());
            showErrorFrame(file_name);
        } catch (IOException e) {
            System.out.println(e);
        } catch (Exception e) {
            System.out.println("Exception in class: FirstClassification. " + e);
        }
    }

    // TODO: This giant method needs to be split up more
    private int doPage(int lines_before, Element page) {
        int page_number = Integer.parseInt(page.getAttribute("number").getValue());

        this.fonts.addAll(getFonts(page));

        List<Text_Column> text_columns =  generateColumns(page, 1);
        for (Text_Column tc : text_columns) {
            this.lines.addAll(tc.lines);
        }

        boolean multi_modus = false;
        int d = 0;
        int sum_of_distances = 0;

        for (int o=lines_before;o<this.lines.size();o++) {
            Line l = this.lines.get(o);
            
            Text_Element.processLineTexts(l.texts);

            if (l.texts.size() > 1) {
                // multi-line
                if (multi_modus == true) {
                    Multiline_Block current_mlb = this.mlbs.get(this.mlbs.size()-1);
                    sum_of_distances += d;
                    current_mlb.add(l);
                } else {
                    Multiline_Block mlb = new Multiline_Block();
                    sum_of_distances = 0;
                    mlb.init(l, o, page_number);
                    this.mlbs.add(mlb);
                    multi_modus = true;
                }
            } else if (l.texts.size() == 1) {
                // single-line
                if (multi_modus == true) {

                    Line pl = this.lines.get(o-1);
                    sum_of_distances += d;
                    Text_Element t = l.texts.get(0);
                    int top_distance = l.first_top - pl.bottom;

                    boolean control = false;

                    int belongs = 0;

                    for (int k=0;k<pl.texts.size();k++) {

                        Text_Element n = pl.texts.get(k);
                        int left_distance = Math.abs(n.left - t.left);
                        int right_distance = Math.abs((n.left + n.width) - (t.left+t.width));

                        if (top_distance < t.height/2 && n.typ.equals(t.typ) 
                                && n.typ == Type.TEXT && ((left_distance < 3) 
                                        || (right_distance < 3))) {

                            String s = n.value + "\n" + t.value;
                            n.value = s;

                            n.count_lines++;
                            this.lines.remove(o);
                            o--;
                            n.add(t);
                            pl.add(t);
                            control = true;
                        }
                        if (Text_Element.intersect(t,n)) {
                            belongs++;
                        }
                    } // end of for
                    if (control == false) {


                        /*	if (belongs == 1)  {
                Multiline_Block current_mlb = (Multiline_Block) this.mlbs.lastElement();
                actualize_mlb_values(current_mlb, l);
                 }
                 else {*/
                        //if (belongs == 0 || count_single_lines > 5) {
                        Multiline_Block current_mlb = this.mlbs.get(this.mlbs.size()-1);
                        int mlb_element_count = current_mlb.end - current_mlb.begin;
                        if (mlb_element_count > 0) {
                            current_mlb.avg_distance = sum_of_distances/mlb_element_count;
                        } else {
                            current_mlb.avg_distance = d;
                        }
                        multi_modus = false;
                        // }
                    }
                }
                else {
                    // do nothing
                }
            }
        }

        multi_modus = false;
        lines_before = this.lines.size();
        return lines_before;
    }


    private List<Text_Column> generateColumns(Element page, int column_count) {
        List<Text_Column> text_columns = new ArrayList<Text_Column>();
        int page_width = Integer.parseInt(page.getAttribute("width").getValue());
        int text_columns_width = page_width/column_count;

        for (int i=0;i<column_count;i++) {
            Text_Column tc = new Text_Column(text_columns_width);
            text_columns.add(tc);
        }
        int distance = 0;
        Text_Column current_tc;

        List<Element> text_elements = new LinkedList<Element>(page.getChildren("text"));
        Collections.sort(text_elements, new TopElementComparator());
        
        for (Element e : text_elements) {
            Text_Element current_t = Text_Element.getTextElement(e, fonts);

            int right_column = Math.abs(current_t.left/text_columns_width);

            if (right_column < text_columns.size()) {

                current_tc = text_columns.get(right_column);

                if (current_tc.lines.size() > 0) {
                    Line l = current_tc.lines.get(current_tc.lines.size()-1);

                    if (l.contains(current_t)) {
                        // exactly in the boundaries of the line
                        l.texts.add(current_t);
                        l.add(current_t);
                    } else {
                        Line new_line = new Line();
                        new_line.texts.add(current_t);
                        new_line.init(current_t);
                        current_tc.lines.add(new_line);
                        distance += new_line.first_top - l.last_top;
                    }
                } else {
                    Line new_line = new Line();
                    new_line.texts.add(current_t);
                    new_line.init(current_t);
                    current_tc.lines.add(new_line);
                } // if current_tc.lines
            } // if right_column ...
        } // for e_array.length
        return text_columns;
    }


    /**
     * Return a list of fonts on a given page.
     */
    private List<Font> getFonts(Element page) {
        List<Font> fonts = new ArrayList<Font>();
        int page_number = Integer.parseInt(page.getAttribute("number").getValue());

        for (Element font : page.getChildren("fontspec")) {
            int id = Integer.parseInt(font.getAttribute("id").getValue());
            int size = Integer.parseInt(font.getAttribute("size").getValue());
            String family = font.getAttribute("family").getValue();
            String color = font.getAttribute("color").getValue();
            Font f = new Font(page_number,id,size,family,color);
            fonts.add(f);
        }
        return fonts;
    }


    /**
     * Merge contents of multi-line blocks.  This will modify the collections
     * in place.
     * 
     * @param blocks
     * @param linez
     */
    private static void multiline_block_merge(List<Multiline_Block> blocks, List<Line> linez) {
        int steps_backward = 0;
        int steps_forward = 0;
        int before = 0;
        int after = 0;

        int removed_elements_before = 0;
        int removed_elements_after = 0;

        for (int i=0;i<blocks.size();i++) {

            Multiline_Block mlb2 = blocks.get(i);

            mlb2.begin = mlb2.begin - removed_elements_before - removed_elements_after;
            mlb2.end = mlb2.end - removed_elements_before - removed_elements_after;

            before = removed_elements_before;
            after = removed_elements_after;

            if (i==0) {
                // first multiline block
                if (mlb2.begin-10 > 0) {
                    steps_backward = 10;
                } else {
                    steps_backward = mlb2.begin - 1;
                }
                steps_forward = 0;
                int[] counts = line_merge(mlb2, linez, steps_backward, steps_forward);
                removed_elements_before += counts[0];
                removed_elements_after += counts[1];
                mlb2.begin = mlb2.begin - (removed_elements_before - before);
                mlb2.end = mlb2.end - (removed_elements_before - before);
            } else if (i == blocks.size() - 1) {
                // last multiline block
                if (mlb2.end + 10 < linez.size()) {
                    steps_forward = 10;
                } else {
                    steps_forward = linez.size() - mlb2.end - 1;
                }
                steps_backward = 0;
                int[] counts = line_merge(mlb2, linez, steps_backward, steps_forward);
                removed_elements_before += counts[0];
                removed_elements_after += counts[1];
            } else {
                // every other multiline block between the first and the last
                Multiline_Block mlb1 = blocks.get(i-1);
                Multiline_Block mlb3 = blocks.get(i+1);

                steps_forward = mlb3.begin - mlb2.end-1;
                steps_backward = mlb2.begin - mlb1.end-1;

                if (mlb2.page == mlb3.page && mlb2.page != mlb1.page) {
                    steps_backward = 0;
                    int[] counts = line_merge(mlb2, linez, steps_backward, steps_forward);
                    removed_elements_before += counts[0];
                    removed_elements_after += counts[1];
                } else if (mlb2.page == mlb1.page && mlb2.page != mlb3.page) {
                    steps_forward = 0;
                    int[] counts = line_merge(mlb2, linez, steps_backward, steps_forward);
                    removed_elements_before += counts[0];
                    removed_elements_after += counts[1];
                } else if (mlb2.page == mlb1.page && mlb2.page == mlb3.page) {
                    int[] counts = line_merge(mlb2, linez, steps_backward, steps_forward);
                    removed_elements_before += counts[0];
                    removed_elements_after += counts[1];
                } // if mlbs on the same page

                boolean merge_with_before = false;

                if (mlb2.begin - mlb1.end <= 3 && mlb2.page == mlb1.page 
                        && (Math.abs(mlb2.max_elements - mlb1.max_elements) <=1))  {
                    mlb1.end = mlb2.end - (removed_elements_before - before);
                    blocks.remove(i);
                    merge_with_before = true;
                    mlb1.add(mlb2);
                    i--;
                }
                if (mlb3.begin - mlb2.end <= 3 && mlb3.page == mlb2.page 
                        && (Math.abs(mlb2.max_elements - mlb3.max_elements) <=1)) {
                    if (merge_with_before == false) {
                        mlb2.begin = mlb2.begin - (removed_elements_before - before);
                        mlb2.end = mlb3.end - (removed_elements_before - before) 
                                - (removed_elements_after - after);
                        mlb2.add(mlb3);
                        blocks.remove(i+1);
                    }
                    else {
                        mlb1.end = mlb3.end
                                - (removed_elements_before - before)
                                - (removed_elements_after - after);
                        mlb1.add(mlb3);
                        blocks.remove(i+1);
                    }
                }
            }
        }
    }


    private static int[] line_merge(Multiline_Block mlb, List<Line> lines, int steps_back, int steps_for) {
        Line first_line = lines.get(mlb.begin);
        Line last_line = lines.get(mlb.end);
        int count = 0;
        int removed_elements_before = 0;
        int removed_elements_after = 0;
        boolean merge_control = true;

        for (int i=1; i<=steps_back && merge_control == true;i++) {
            Line pl = lines.get(mlb.begin - i);
            List<Text_Element> storage = new ArrayList<Text_Element>(first_line.texts);

            int top_distance = first_line.first_top - pl.bottom;

            for (int j = 0; j < first_line.texts.size(); j++) {
                Text_Element t = storage.get(j);

                for (int k = 0; k < pl.texts.size(); k++) {

                    Text_Element n = pl.texts.get(k);
                    int left_distance = Math.abs(n.left - t.left);
                    int right_distance = Math.abs((n.left + n.width)
                            - (t.left + t.width));
                    if (top_distance < t.height / 2 && t.typ.equals(n.typ)
                            && t.typ.equals("text")
                            && ((left_distance < 3) || (right_distance < 3))) {
                        String s = n.value + " " + t.value;
                        t.value = s;
                        t.count_lines++;
                        t.add(n);
                        count++;
                    }
                }
            }
            if (count == pl.texts.size()) {
                List<Text_Element> clone = new ArrayList<Text_Element>(storage);
                first_line.texts = clone;
                for (int p = 0; p < first_line.texts.size(); p++) {
                    Text_Element t = first_line.texts.get(p);
                    first_line.add(t);
                }

                lines.remove(mlb.begin - i);
                removed_elements_before++; 
            } else {
                merge_control = false;
            }
            count = 0;

        }
        merge_control = true;
        
        for (int i = 1; i <= steps_for && merge_control == true; i++) {
            Line nl = lines.get(mlb.end + i);
            List<Text_Element> storage = new ArrayList<Text_Element>(
                    last_line.texts);

            int top_distance = nl.first_top - last_line.bottom;

            for (int j = 0; j < last_line.texts.size(); j++) {
                Text_Element t = last_line.texts.get(j);
                for (int k = 0; k < nl.texts.size(); k++) {
                    Text_Element n = nl.texts.get(k);
                    int left_distance = Math.abs(n.left - t.left);
                    int right_distance = Math.abs((n.left + n.width)
                            - (t.left + t.width));

                    if (top_distance < t.height / 2 && t.typ.equals(n.typ)
                            && t.typ == Type.TEXT
                            && ((left_distance < 3) || (right_distance < 3))) {

                        String s = t.value + " " + n.value;
                        t.value = s;
                        t.count_lines++;
                        t.add(n);
                        count++;
                    }
                }
            }
            if (count == nl.texts.size()) {
                last_line.texts = new ArrayList<Text_Element>(storage);
                for (int p = 0; p < last_line.texts.size(); p++) {
                    Text_Element t = last_line.texts.get(p);
                    last_line.add(t);
                }
                lines.remove(mlb.end + i);
                removed_elements_after++;
            } else {
                merge_control = false;
            }
            count = 0;
        }
        return new int[] {removed_elements_before, removed_elements_after};
    }
	
    private void showErrorFrame(final String file_name) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Frame f = new Frame(ge.getDefaultScreenDevice().getDefaultConfiguration());

        Dialog d = new Dialog(f, "Failure", true);
        Label l = new Label("pdftohtml was unable to return right data.");
        Label l2 = new Label("Would you like to restart with pre-debugging?");
        d.setLayout(null);
        l.setBounds(60,50,300,20);
        l2.setBounds(60,70,300,20);
        d.add(l);
        d.add(l2);
        d.setSize(420,150);

        Button b = new Button("Yes");
        b.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Button b2 = (Button) evt.getSource();
                debug_pdftohtml_output(file_name);
                ((Dialog)b2.getParent()).dispose();
            }
        });
        b.setBounds(180,100,60,20);

        Button b2 = new Button("No");
        b2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Button b3 = (Button) evt.getSource();
                ((Dialog)b3.getParent()).dispose();
            }
        });
        b.setBounds(180,100,60,20);
        b2.setBounds(250,100,60,20);

        d.add(b);
        d.add(b2);
        d.setLocationRelativeTo(null);
        d.setVisible(true);
    }
    
    private void debug_pdftohtml_output(final String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    filename));

            PrintStream dos = new PrintStream(new FileOutputStream(new File(
                    this.path + File.separator + "debugged_output.xml")));

            String current_line = br.readLine();

            while (current_line != null) {
                current_line = current_line.replaceAll("A href", "a href");
                current_line = current_line.replaceAll("<B>", "<b>");
                current_line = current_line.replaceAll("<I>", "<i>");
                current_line = current_line.replaceAll("</I>", "</i>");
                current_line = current_line.replaceAll("</B>", "</b>");

                dos.println(current_line);
                current_line = br.readLine();
            }

            run(this.path + File.separator + "debugged_output.xml");

            dos.close();
            br.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

}