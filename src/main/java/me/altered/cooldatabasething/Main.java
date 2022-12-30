package me.altered.cooldatabasething;

import me.altered.cooldatabasething.model.Country;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.*;
import java.sql.SQLException;
import java.util.Objects;

public class Main {

    private static final String path = "countries.db";

    public static void main(String[] args) throws SQLException, IOException {
        // Подключаемся к базе данных
        var database = DatabaseFactory.getDatabase(path);
        if (database == null) {
            System.out.println("Failed to create database");
            return;
        }

        // Создаем таблицу, если ее еще нет
        var utils = new ConnectionUtils(database);
        try {
            utils.createCountriesTable();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return;
        }

        // Заполняем таблицу, если она пуста
        try (var result = utils.selectAll()) {
            if (!result.next()) fillTable(utils);
        }

        // Задача 2.
        // Выведите в консоль страну с самым высоким показателем
        // экономики среди "Latin America and Caribbean" и "Eastern Asia"
        var economyCountry = getCountryWithMaxEconomy(utils);
        System.out.println("Страна с самым высоким показателем экономики: " +
                (economyCountry != null ? economyCountry.name() : null));

        // Задача 3.
        // Найдите страну с "самыми средними показателями" среди "Western Europe" и "North America"
        var averageCountry = getCountryWithAverageStats(utils);
        System.out.println("Страна с самыми средними показателями: " +
                (averageCountry != null ? averageCountry.name() : null));

        // Задача 1.
        // Сформируйте график по показателю экономики, объединив их по странам
        buildEconomyGraph(utils);
    }

    private static void fillTable(ConnectionUtils utils) throws IOException, SQLException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Main.class.getResourceAsStream("/happiness.csv"))))) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                var country = Country.fromRow(line);
                if (country != null) utils.insert(country);
            }
        }
    }

    private static void buildEconomyGraph(ConnectionUtils utils) throws SQLException {
        var dataset = new DefaultCategoryDataset();
        try (var result = utils.selectAllOrdered()) {
            while (result.next()) {
                var country = Country.fromRow(result);
                if (country == null) continue;
                dataset.addValue(country.economy(), country.name(), "");
            }
        }

        var chart = ChartFactory.createBarChart(null, "Страны", null, dataset,
                PlotOrientation.VERTICAL, true, false, false);
        var panel = new ChartPanel(chart);
        panel.setPreferredSize(new Dimension(1280, 720));
        var frame = new ApplicationFrame("Показатели экономики");
        frame.setContentPane(panel);
        frame.pack();
        frame.setVisible(true);
    }

    private static Country getCountryWithMaxEconomy(ConnectionUtils utils) throws SQLException {
        try (var result = utils.query("""
            SELECT * FROM COUNTRIES WHERE REGION = 'Latin America and Caribbean' OR REGION = 'Eastern Asia'
            ORDER BY ECONOMY DESC
        """)) {
            if (result.next()) return Country.fromRow(result);
        }
        return null;
    }

    private static Country getCountryWithAverageStats(ConnectionUtils utils) throws SQLException {
        try (var result = utils.query("""
            WITH AVERAGE AS (SELECT avg("HAPPINESS SCORE") + avg("STANDARD ERROR") + avg(ECONOMY) + avg(FAMILY) +
            avg(HEALTH) + avg(FREEDOM) + avg(TRUST) + avg(GENEROSITY) + avg("DYSTOPIA RESIDUAL") FROM COUNTRIES)
            SELECT * FROM COUNTRIES WHERE REGION = 'Western Europe' OR REGION = 'North America'
            ORDER BY abs("HAPPINESS SCORE" + "STANDARD ERROR" + ECONOMY + FAMILY + HEALTH + FREEDOM +
            TRUST + GENEROSITY + "DYSTOPIA RESIDUAL" - (SELECT * FROM AVERAGE));
        """)) {
            if (result.next()) return Country.fromRow(result);
        }
        return null;
    }
}
