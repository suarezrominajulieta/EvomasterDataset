package org.menu.api.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code DishID,Type,Name,Description,Price} format of {@code setup/dishes.csv}.
 *
 * <p>Plain comma splitting, the way the .NET seed did it: the format has no quoting, and commas
 * inside a description are written as colons and put back here. The seed and the import endpoint
 * share this so that a file taken straight out of {@code src/main/resources/setup} can be posted
 * back to {@code /api/v1/Foods/import} and land the same way.
 */
public final class DishCsvParser {

    public static final String HEADER = "DishID,Type,Name,Description,Price";

    private static final int COLUMNS = 5;

    /** One row, with the description already un-escaped. */
    public record Row(String dishId, String categoryName, String name, String description,
                      BigDecimal price, int lineNumber) {
    }

    private DishCsvParser() {
    }

    /**
     * @throws InvalidRequestException when the document is not this format at all
     */
    public static List<Row> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("the CSV is empty");
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (!lines[0].trim().equalsIgnoreCase(HEADER)) {
            throw new InvalidRequestException(
                    "the first line has to be the header " + HEADER + ", not " + lines[0].trim());
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            String[] columns = line.split(",", -1);
            if (columns.length != COLUMNS) {
                throw new InvalidRequestException(
                        "line " + lineNumber + " has " + columns.length + " columns, expected "
                                + COLUMNS + "; write a comma inside a description as a colon");
            }
            BigDecimal price;
            try {
                price = new BigDecimal(columns[4].trim());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(
                        "line " + lineNumber + " has an unreadable price: " + columns[4].trim());
            }
            rows.add(new Row(
                    columns[0].trim(),
                    columns[1].trim(),
                    columns[2].trim(),
                    // The file escapes its commas as colons, so they go back here.
                    columns[3].trim().replace(':', ','),
                    price,
                    lineNumber));
        }
        if (rows.isEmpty()) {
            throw new InvalidRequestException("the CSV carries a header but no dishes");
        }
        return rows;
    }
}
