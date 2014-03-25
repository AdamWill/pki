/* --- BEGIN COPYRIGHT BLOCK ---
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright (C) 2013 Red Hat, Inc.
 * All rights reserved.
 * --- END COPYRIGHT BLOCK ---
 *
 * @author Endi S. Dewata
 */

var Model = Backbone.Model.extend({
    parseResponse: function(response) {
        return response;
    },
    parse: function(response, options) {
        return this.parseResponse(response);
    },
    createRequest: function(attributes) {
        return attributes;
    },
    save: function(attributes, options) {
        var self = this;
        if (attributes == undefined) attributes = self.attributes;
        // convert attributes into JSON request
        var request = self.createRequest(attributes);
        // remove old attributes
        if (self.isNew()) self.clear();
        // send JSON request
        Model.__super__.save.call(self, request, options);
    }
});

var Collection = Backbone.Collection.extend({
    urlRoot: null,
    initialize: function(options) {
        var self = this;

        self.options = options;
        self.links = {};
        self.query({});
    },
    url: function() {
        return this.currentURL;
    },
    parse: function(response) {
        var self = this;

        // get total entries
        self.total = self.getTotal(response);

        // parse links
        var links = self.getLinks(response);
        links = links == undefined ? [] : [].concat(links);
        self.parseLinks(links);

        // convert entries into models
        var models = [];
        var entries = self.getEntries(response);
        entries = entries == undefined ? [] : [].concat(entries);

        _(entries).each(function(entry) {
            var model = self.parseEntry(entry);
            models.push(model);
        });

        return models;
    },
    getTotal: function(response) {
        return response.total;
    },
    getEntries: function(response) {
        return null;
    },
    getLinks: function(response) {
        return null;
    },
    parseEntry: function(entry) {
        return null;
    },
    parseLinks: function(links) {
        var self = this;
        self.links = {};
        _(links).each(function(link) {
            var name = link.rel;
            var href = link.href;
            self.links[name] = href;
        });
    },
    link: function(name) {
        return this.links[name];
    },
    go: function(name) {
        var self = this;
        if (self.links[name] == undefined) return;
        self.currentURL = self.links[name];
    },
    query: function(params) {
        var self = this;

        // add default options into the params
        _.defaults(params, self.options);

        // generate query string
        var query = "";
        _(params).each(function(value, name) {
            // skip null or empty string, but don't skip 0
            if (value === null || value === "") return;
            query = query == "" ? "?" : query + "&";
            query = query + name + "=" + encodeURIComponent(value);
        });

        self.currentURL = self.urlRoot + query;
    }
});

var Page = Backbone.View.extend({
    initialize: function(options) {
        var self = this;
        Page.__super__.initialize.call(self, options);

        self.url = options.url;
    },
    load: function(container) {
    }
});

var Navigation = Backbone.View.extend({
    initialize: function(options) {
        var self = this;
        Navigation.__super__.initialize.call(self, options);

        self.content = options.content;
        self.pages = options.pages;
        self.homePage = options.homePage;

        $("li", self.$el).each(function(index) {
            var li = $(this);
            var link = $("a", li);
            var url = link.attr("href");
            link.click(function(e) {
                if (url == "#logout") {
                    if (options.logout) {
                        options.logout.call(self);
                    }

                } else if (url.charAt(0) == "#" && url.length > 1) {
                    // get page name
                    var name = url.substring(1);
                    self.load(name);
                }
                e.preventDefault();
            });
        });

        if (self.homePage) self.load(self.homePage);
    },
    load: function(name) {
        var self = this;
        var page = self.pages[name];
        if (!page) {
            alert("Invalid page: " + name);
            return;
        }
        self.content.load(page.url, function(response, status, xhr) {
            page.load(self.content);
        });
    }
});

var Dialog = Backbone.View.extend({
    initialize: function(options) {
        var self = this;
        Dialog.__super__.initialize.call(self, options);

        self.title = options.title;

        self.readonly = options.readonly;
        // by default all fields are editable
        if (self.readonly == undefined) self.readonly = [];

        self.actions = options.actions;
        if (self.actions == undefined) {
            // by default all buttons are active
            self.actions = [];
            self.$("button").each(function(index) {
                var button = $(this);
                var action = button.attr("name");
                self.actions.push(action);
            });
        }

        self.handlers = {};

        // add default cancel handler
        self.handlers["cancel"] = function() {
            self.close();
        };
    },
    render: function() {
        var self = this;

        if (self.title) {
            self.$("header h1").text(self.title);
        }

        self.$(".rcue-button-close").click(function(e) {
            self.close();
            e.preventDefault();
        });

        // setup input fields
        self.$("input").each(function(index) {
            var input = $(this);
            var name = input.attr("name");
            if ( _.contains(self.readonly, name)) {
                input.attr("readonly", "readonly");
            } else {
                input.removeAttr("readonly");
            }
        });

        // setup buttons
        self.$("button").each(function(index) {
            var button = $(this);
            var action = button.attr("name");

            if (_.contains(self.actions, action)) {
                // enable buttons for specified actions
                button.show();
                button.click(function(e) {
                    var handler = self.handlers[action];
                    handler.call(self);
                    e.preventDefault();
                });

            } else {
                // hide unused buttons
                button.hide();
            }
        });

        self.load();
    },
    handler: function(name, handler) {
        var self = this;
        self.handlers[name] = handler;
    },
    open: function() {
        var self = this;
        self.render();
        self.$el.show();
    },
    close: function() {
        var self = this;
        self.$el.hide();

        // remove event handlers
        self.$(".rcue-button-close").off("click");
        self.$("button").each(function(index) {
            var button = $(this);
            button.off("click");
        });
        self.trigger("close");
    },
    load: function() {
        var self = this;

        $("input", self.$el).each(function(index) {
            var input = $(this);
            self.loadField(input);
        });
    },
    loadField: function(input) {
        var self = this;
        var name = input.attr("name");
        var value = self.attributes[name];
        if (!value) value = "";
        input.val(value);
    },
    save: function() {
        var self = this;

        $("input", self.$el).each(function(index) {
            var input = $(this);
            self.saveField(input);
        });
    },
    saveField: function(input) {
        var self = this;
        var name = input.attr("name");
        var value = input.val();
        self.attributes[name] = value;
    }
});

var TableItem = Backbone.View.extend({
    initialize: function(options) {
        var self = this;
        TableItem.__super__.initialize.call(self, options);
        self.table = options.table;
        self.reset();
    },
    reset: function() {
        var self = this;
        $("td", self.$el).each(function(index) {
            var td = $(this);
            var name = td.attr("name");

            if (td.hasClass("pki-select-column")) {
                // uncheck checkbox and reset the value
                var checkbox = $("input[type='checkbox']", td);
                checkbox.attr("checked", false);
                checkbox.val("");

                // hide checkbox by hiding the label
                $("label", td).hide();

            } else if (name == "id") {
                // hide the content
                td.children().hide();

            } else {
                // empty the content
                td.html("&nbsp;");
            }
        });
    },
    render: function() {
        var self = this;
        var prefix = self.table.$el.attr("name") + "_select_";

        $("td", self.$el).each(function(index) {
            var td = $(this);
            var name = td.attr("name");

            if (td.hasClass("pki-select-column")) {
                // generate a unique id based on model id
                var id = prefix + self.id();

                // set the unique id and value for checkbox
                var checkbox = $("input[type='checkbox']", td);
                checkbox.attr("id", id);
                checkbox.attr("checked", false);
                checkbox.val(self.id());

                // point the label to the checkbox and make it visible
                var label = $("label", td);
                label.attr("for", id);
                label.show();

            } else if (name == "id") {
                // setup link to edit dialog
                td.empty();
                $("<a/>", {
                    href: "#",
                    text: self.id(),
                    click: function(e) {
                        self.table.open(self);
                        e.preventDefault();
                    }
                }).appendTo(td);

            } else {
                self.renderColumn(td);
            }
        });
    },
    id: function() {
        var self = this;
        return self.model.id;
    },
    renderColumn: function(td) {
        var self = this;
        var name = td.attr("name");

        // show cell content in plain text
        td.text(self.model.get(name));

        // update cell automatically on model change
        self.model.on("change:" + name, function(event) {
            td.text(self.model.get(name));
        });
    }
});

var Table = Backbone.View.extend({
    initialize: function(options) {
        var self = this;

        Table.__super__.initialize.call(self, options);
        self.addDialog = options.addDialog;
        self.editDialog = options.editDialog;
        self.tableItem = options.tableItem || TableItem;

        // number of table rows
        self.pageSize = options.pageSize || 5;

        // current page: 1, 2, 3, ...
        self.page = 1;
        self.totalPages = 1;

        self.thead = $("thead", self.$el);

        // setup search field handler
        self.searchField = $("input[name='search']", self.thead);
        self.searchField.keypress(function(e) {
            if (e.which == 13) {
                // show the first page of search results
                self.page = 1;
                self.render();
            }
        });

        // setup add button handler
        $("button[name='add']", self.thead).click(function(e) {
            self.add();
        });

        // setup remove button handler
        $("button[name='remove']", self.thead).click(function(e) {
            var items = [];
            var message = "Are you sure you want to remove the following entries?\n";

            // get selected items
            $("input:checked", self.tbody).each(function(index) {
                var input = $(this);
                var id = input.val();
                if (id == "") return;
                items.push(id);
                message = message + " - " + id + "\n";
            });

            if (items.length == 0) return;
            if (!confirm(message)) return;

            self.remove(items);
        });

        // setup select all handler
        self.selectAllCheckbox = $("input[type='checkbox']", self.thead);
        self.selectAllCheckbox.click(function(e) {
            var checked = $(this).is(":checked");
            $("input[type='checkbox']", self.tbody).prop("checked", checked);
        });

        self.tbody = $("tbody", self.$el);
        self.template = $("tr", self.tbody).detach();

        // create empty rows
        self.items = [];
        for (var i = 0; i < self.pageSize; i++) {
            var tr = self.template.clone();
            var item = new self.tableItem({
                el: tr,
                table: self
            });
            self.items.push(item);
            self.tbody.append(tr);
        }

        self.tfoot = $("tfoot", self.$el);
        self.totalEntriesField = $("span[name='totalEntries']", self.tfoot);
        self.pageField = $("input[name='page']", self.tfoot);
        self.totalPagesField = $("span[name='totalPages']", self.tfoot);

        // setup page jump handler
        self.pageField.keypress(function(e) {
            if (e.which == 13) {
                // parse user entered page number
                self.page = parseInt(self.pageField.val());
                if (isNaN(self.page)) self.page = 1;

                // make sure 1 <= page <= total pages
                self.page = Math.max(self.page, 1);
                self.page = Math.min(self.page, self.totalPages);
                self.render();
            }
        });

        // setup handlers for first, prev, next, and last buttons
        $("a[name='first']", self.tfoot).click(function(e) {
            self.page = 1;
            self.render();
            e.preventDefault();
        });
        $("a[name='prev']", self.tfoot).click(function(e) {
            self.page = Math.max(self.page - 1, 1);
            self.render();
            e.preventDefault();
        });
        $("a[name='next']", self.tfoot).click(function(e) {
            self.page = Math.min(self.page + 1, self.totalPages);
            self.render();
            e.preventDefault();
        });
        $("a[name='last']", self.tfoot).click(function(e) {
            self.page = self.totalPages;
            self.render();
            e.preventDefault();
        });
    },
    render: function() {
        var self = this;

        // set query based on current page, page size, and filter
        self.collection.query({
            start: (self.page - 1) * self.pageSize,
            size: self.pageSize,
            filter: self.searchField.val()
        });

        // fetch data based on query
        self.collection.fetch({
            reset: true,
            success: function(collection, response, options) {

                // update controls
                self.renderControls();

                // display entries
                _(self.items).each(function(item, index) {
                    self.renderRow(item, index);
                });
            },
            error: function(collection, response, options) {
                alert(response.statusText);
            }
        });
    },
    renderControls: function() {
        var self = this;

        // clear selection
        self.selectAllCheckbox.attr("checked", false);

        // display total entries
        self.totalEntriesField.text(self.totalEntries());

        // display current page number
        self.pageField.val(self.page);

        // calculate and display total number of pages
        self.totalPages = Math.floor(Math.max(0, self.totalEntries() - 1) / self.pageSize) + 1;
        self.totalPagesField.text(self.totalPages);
    },
    renderRow: function(item, index) {
        var self = this;
        if (index < self.collection.length) {
            // show entry in existing row
            item.model = self.collection.at(index);
            item.render();

        } else {
            // clear unused row
            item.reset();
        }
    },
    totalEntries: function() {
        var self = this;
        return self.collection.total;
    },
    open: function(item) {
        var self = this;

        var dialog = self.editDialog;
        var model = item.model;
        dialog.attributes = _.clone(model.attributes);

        dialog.handler("save", function() {

            // save attribute changes
            dialog.save();
            model.set(dialog.attributes);

            // if nothing has changed, return
            var changedAttributes = model.changedAttributes();
            if (!changedAttributes) return;

            // save changed attributes with PATCH
            model.save(changedAttributes, {
                patch: true,
                wait: true,
                success: function(model, response, options) {
                    // redraw table after saving entries
                    self.render();
                    dialog.close();
                },
                error: function(model, response, options) {
                    if (response.status == 200) {
                        // redraw table after saving entries
                        self.render();
                        dialog.close();
                        return;
                    }
                    alert("ERROR: " + response.responseText);
                }
            });
        });

        // load data from server
        model.fetch({
            success: function(model, response, options) {
                dialog.open();
            },
            error: function(model, response, options) {
                alert("ERROR: " + response);
            }
        });
    },
    add: function() {
        var self = this;

        var dialog = self.addDialog;
        dialog.attributes = {};

        dialog.handler("add", function() {

            // save new attributes
            dialog.save();
            var attributes = {};
            _.each(dialog.attributes, function(value, key) {
                if (value == "") return;
                attributes[key] = value;
            });

            // save new entry with POST
            var model = new self.collection.model();
            model.save(attributes, {
                wait: true,
                success: function(model, response, options) {
                    // redraw table after adding new entry
                    self.render();
                    dialog.close();
                },
                error: function(model, response, options) {
                    if (response.status == 201) {
                        // redraw table after adding new entry
                        self.render();
                        dialog.close();
                        return;
                    }
                    alert("ERROR: " + response.responseText);
                }
            });
        });

        dialog.open();
    },
    remove: function(items) {
        var self = this;

        // remove selected entries
        _.each(items, function(id, index) {
            var model = self.collection.get(id);
            model.destroy({
                wait: true
            });
        });

        self.render();
    }
});

var PropertiesTableItem = TableItem.extend({
    initialize: function(options) {
        var self = this;
        PropertiesTableItem.__super__.initialize.call(self, options);
    },
    id: function() {
        var self = this;
        return self.property.name;
    },
    renderColumn: function(td) {
        var self = this;
        td.text(self.property.value);
    }
});

var PropertiesTable = Table.extend({
    initialize: function(options) {
        var self = this;
        options.tableItem = options.tableItem || PropertiesTableItem;
        PropertiesTable.__super__.initialize.call(self, options);
        self.properties = options.properties;
    },
    render: function() {
        var self = this;

        // perform manual filter
        var filter = self.searchField.val();
        if (filter === "") {
            self.entries = self.properties;

        } else {
            self.entries = [];
            _(self.properties).each(function(item, index) {
                // select properties whose names or values contain the filter
                if (item.name.indexOf(filter) >= 0 || item.value.indexOf(filter) >= 0) {
                    self.entries.push(item);
                }
            });
        }

        self.renderControls();

        _(self.items).each(function(item, index) {
            self.renderRow(item, index);
        });
    },
    renderRow: function(item, index) {
        var self = this;
        var i = (self.page - 1) * self.pageSize + index;
        if (i < self.entries.length) {
            // show entry in existing row
            item.property = self.entries[i];
            item.render();

        } else {
            // clear unused row
            item.reset();
        }
    },
    totalEntries: function() {
        var self = this;
        return self.entries.length;
    },
    open: function(item) {
        var self = this;

        var dialog = self.editDialog;
        dialog.attributes = _.clone(item.property);

        dialog.handler("save", function() {

            // save property changes
            dialog.save();
            _.extend(item.property, dialog.attributes);

            // redraw table after saving property changes
            self.render();
            dialog.close();
        });

        dialog.open();
    },
    add: function() {
        var self = this;

        var dialog = self.addDialog;
        dialog.attributes = {};

        dialog.handler("add", function() {

            // save new property
            dialog.save();
            self.properties.push(dialog.attributes);

            // sort properties by name
            self.properties = _.sortBy(self.properties, function(property) {
                return property.name;
            });

            // redraw table after adding new property
            self.render();
            dialog.close();
        });

        dialog.open();
    },
    remove: function(items) {
        var self = this;

        // remove selected properties
        self.properties = _.reject(self.properties, function(property) {
            return _.contains(items, property.name);
        });

        // redraw table after removing properties
        self.render();
    }
});
