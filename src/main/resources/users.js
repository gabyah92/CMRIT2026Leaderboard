document.addEventListener('DOMContentLoaded', function () {
    let rowData = [];

    $.ajax({
        url: 'https://raw.githubusercontent.com/gabyah92/CMRIT2026Leaderboard/main/src/main/resources/participant_details.csv',
        success: function (csvData) {
            Papa.parse(csvData, {
                header: true,
                complete: function (results) {
                    rowData = results.data;
                    console.log(rowData); // This will log the parsed CSV data
                }
            });

            console.log(rowData); // This will log the populated rowData array
            console.log(rowData[0]['GeeksForGeeks URL Exists']); // This will log the value of the first row's 'GeeksForGeeks URL Exists' column

            const gridOptions = {
                columnDefs: [
                    {
                        headerName: "#",
                        field: "rowIndex",
                        valueGetter: "node.rowIndex + 1",
                        width: 60,
                        pinned: 'left',
                        filter: false,
                        sortable: false,
                        lockPosition: true
                    },
                    {
                        headerName: 'Handle',
                        field: 'Handle',
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'GeeksForGeeks Handle',
                        field: 'GeeksForGeeks Handle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('GeeksForGeeks Handle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'Codeforces Handle',
                        field: 'Codeforces Handle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('Codeforces Handle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'LeetCode Handle',
                        field: 'LeetCode Handle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('LeetCode Handle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'CodeChef Handle',
                        field: 'CodeChef Handle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('CodeChef Handle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'HackerRank Handle',
                        field: 'HackerRank Handle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('HackerRank Handle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                ],
                defaultColDef: {
                    sortable: false,
                    filter: false,
                },
                rowData: rowData, // Assign the populated rowData array here
            };

            const gridDiv = document.querySelector('#myGrid');
            new agGrid.Grid(gridDiv, gridOptions);
        }
    });

    /**
     * Check if a URL exists for a given column and row index.
     *
     * @param {string} col - The column name.
     * @param {number} rowIndex - The index of the row.
     * @returns {boolean} - True if the URL exists, false otherwise.
     */
    function check_url(col, rowIndex) {
        let url_exists;
        console.log("rowIndex: " + rowIndex);
        console.log("col: " + col);
        if (col === 'GeeksForGeeks Handle') {
            url_exists = rowData[rowIndex]['GeeksForGeeks URL Exists'];
            return url_exists === 'True';
        }
        if (col === 'Codeforces Handle') {
            url_exists = rowData[rowIndex]['Codeforces URL Exists'];
            return url_exists === 'True';
        }
        if (col === 'LeetCode Handle') {
            url_exists = rowData[rowIndex]['LeetCode URL Exists'];
            return url_exists === 'True';
        }
        if (col === 'CodeChef Handle') {
            url_exists = rowData[rowIndex]['CodeChef URL Exists'];
            console.log("url_exists for CodeChef: " + url_exists);
            return url_exists === 'True';
        }
        if (col === 'HackerRank Handle') {
            url_exists = rowData[rowIndex]['HackerRank URL Exists'];
            console.log("url_exists for HackerRank: " + url_exists);
            return url_exists === 'True';
        }
    }

});
